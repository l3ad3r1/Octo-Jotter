# On-Device AI — Design Spec

Status: **Draft / proposal**
Owner: l3ad3r1
Last updated: 2026-08-09

This document specifies how Octo Jotter adds AI features (semantic search,
chat-with-your-notes, inline completion, agentic filing, voice capture) that run
**entirely on the device**, with no note content leaving the phone. It reuses the
llama.cpp runtime already proven in the sibling **Hermes Agent Android** project.

The design goal is a strict superset of privacy: everything that works offline
today keeps working, and the AI layer is an **opt-in, device-gated** addition —
never a hard dependency and never a data-exfiltration path.

---

## 1. Motivation

[fynnfluegge/rocketnotes](https://github.com/fynnfluegge/rocketnotes) demonstrates
a compelling AI note-taking feature set (RAG chat, semantic search, agentic
Zettelkasten filing, Copilot-style completion). It achieves this with cloud LLMs
or a local Ollama server on a desktop.

Octo Jotter is offline-first and mobile-only, and its identity is *your notes stay
yours*. Shipping those features via a cloud LLM would contradict that. The
alternative — running the model **on the phone** — is now realistic because Hermes
already ships a working llama.cpp GGUF runtime for Android. This spec is the plan
to bring that capability into Octo Jotter without compromising the offline-first,
private-by-default contract.

---

## 2. Goals / Non-Goals

### Goals
- **G1** Semantic search over all notes, ranked by meaning, not just substring.
- **G2** RAG chat: ask questions and get answers grounded in the user's own notes,
  with citations back to source notes.
- **G3** Inline "copilot" completion inside the markdown editor.
- **G4** Agentic filing: suggest a folder + tags for a new/inbox note.
- **G5** Voice capture: dictate note content.
- **G6** 100% on-device inference. No note text, embedding, or prompt is ever sent
  off the device by this feature.
- **G7** Fully **opt-in** and **device-gated**. On unsupported devices the feature
  is invisible and the app behaves exactly as it does today.

### Non-Goals
- **N1** Hosting an MCP *server* on the phone (rocketnotes does this on desktop).
  Out of scope for v1; may revisit as an MCP *client* later.
- **N2** Cross-device / cloud vector sync. Embeddings are local-only, rebuildable.
- **N3** Training or fine-tuning models on-device.
- **N4** Bundling model weights in the APK (see §7 — models are downloaded on first
  use).
- **N5** Replacing the existing editor/sync/plugin systems. This is additive.

---

## 3. Reuse from Hermes Agent Android

Hermes has already solved the expensive, risky parts. The following are **lift and
adapt**, not build-from-scratch:

| Component (Hermes path) | What it gives us | Action |
|---|---|---|
| `app/src/main/cpp/` (`llama.cpp`, `ai_chat.cpp`, `CMakeLists.txt`) | Native GGUF inference, streaming tokens, chat templating | Vendor-copy into a new `:ondevice-llm` module |
| `com.arm.aichat.InferenceEngine` + `internal.InferenceEngineImpl` | JNI wrapper, singleton lifecycle, single-thread dispatcher, model load/unload | Copy as-is; it is app-agnostic |
| `com.hermes.agent.data.llm.LocalLlmProvider` / `LocalLlmManager` | High-level "messages in, token flow out" provider; prompt assembly (incl. the double-templating fix) | Copy, strip Hermes-specific tool/skill plumbing |
| `data.memory.EmbeddingService` (interface) | Clean embedding contract (`dimension`, `embed`, `embedAll`, L2-normalized) | Copy the **interface**; replace the impl (see below) |
| `data.memory.HashingEmbeddingService` | **Mock only** — SHA-256 pseudo-vectors, semantically meaningless | **Do NOT reuse.** This is the one real gap; we ship a real embedder |
| `build.gradle.kts` externalNativeBuild block | Correct llama.cpp CMake flags for Android arm64 (`GGML_BACKEND_DL=ON`, CPU variants, Vulkan off) | Copy the flags |

**The single genuine gap: embeddings.** Hermes only has a hashing mock for vectors.
Octo Jotter must ship a *real* embedding model (see §6.1). Everything else is a port.

> Reuse mechanism: for v1, **vendor-copy** the native + JNI + provider code into a
> new Gradle module rather than sharing a repo. A shared library across two private
> repos adds release-coupling we don't want yet. Revisit extraction into a shared
> published module once both apps stabilize.

---

## 4. Architecture Overview

New Gradle module `:ondevice-llm` (native + inference), plus new packages under the
existing `:app` module. Nothing below `com.l3ad3r1.octojotter` changes shape; we add
an `ai` subtree.

```
:ondevice-llm  (new module)
  cpp/                       # vendored llama.cpp + ai_chat.cpp
  com.l3ad3r1.ondevice
    InferenceEngine          # JNI wrapper (from Hermes com.arm.aichat)
    LocalLlmProvider         # messages -> Flow<token>
    EmbeddingModel           # real embedder (see §6.1)

:app
  com.l3ad3r1.octojotter.ai
    AiCapability             # device gating (§8)
    model/ModelManager       # download / verify / delete GGUF + embedder (§7)
    index/
      NoteChunker            # note -> chunks
      NoteIndexer            # WorkManager job: chunk -> embed -> store
      VectorStore            # Room-backed cosine search (§6.2)
    search/SemanticSearch    # G1 — hybrid keyword + vector
    chat/RagChatEngine       # G2 — retrieve -> prompt -> stream, with citations
    complete/CompletionEngine# G3 — editor copilot
    file/NoteClassifier      # G4 — folder + tag suggestions
    voice/VoiceCapture       # G5 — SpeechRecognizer wrapper
  com.l3ad3r1.octojotter.data.local
    NoteEmbeddingEntity      # new Room entity (§5)
    NoteEmbeddingDao
```

### Data flow (indexing)
```
Note saved  ──►  NoteIndexer (WorkManager, deferred)
                   │  NoteChunker: content -> ~512-token chunks (markdown-aware)
                   │  EmbeddingModel.embedAll(chunks) -> FloatArray[384]
                   └► VectorStore.upsert(noteId, chunkVectors)  (Room)
```

### Data flow (query — RAG chat)
```
User question
   │ EmbeddingModel.embed(question)
   │ VectorStore.topK(qVec, k=6)  ─► note chunks + noteIds
   │ Build grounded prompt (system + retrieved chunks + question)
   └ LocalLlmProvider.stream(prompt) ─► tokens ─► UI, with [#noteId] citations
```

---

## 5. Data Model Changes

Current DB is **`AppDatabase` version 10**. This feature bumps it to **version 11**
with an **additive, non-destructive** migration (same pattern as the existing
`MIGRATION_6_7` etc.). No existing column changes; existing notes are untouched.

### New entity — `NoteEmbeddingEntity`
```kotlin
@Entity(
    tableName = "note_embeddings",
    foreignKeys = [ForeignKey(
        entity = NoteEntity::class,
        parentColumns = ["id"],
        childColumns = ["noteId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("noteId")]
)
data class NoteEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Int,              // FK -> notes.id
    val chunkIndex: Int,          // 0-based chunk within the note
    val chunkText: String,        // the source chunk (for RAG context + citation)
    val vector: ByteArray,        // 384 floats, little-endian, L2-normalized
    val contentHash: String,      // hash of chunkText; skip re-embed if unchanged
    val model: String,            // embedder id/version, so we can invalidate on upgrade
    val embeddedAt: Long = System.currentTimeMillis()
)
```

`vector` is stored as a packed `ByteArray` (384 × 4 bytes = 1536 bytes/chunk). A
`Converters` entry (or manual pack/unpack in the DAO layer) handles Float[]↔ByteArray.

### Migration `MIGRATION_10_11`
```kotlin
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS note_embeddings (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                noteId INTEGER NOT NULL,
                chunkIndex INTEGER NOT NULL,
                chunkText TEXT NOT NULL,
                vector BLOB NOT NULL,
                contentHash TEXT NOT NULL,
                model TEXT NOT NULL,
                embeddedAt INTEGER NOT NULL,
                FOREIGN KEY(noteId) REFERENCES notes(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_note_embeddings_noteId ON note_embeddings(noteId)")
    }
}
```

Register in `AppDatabase`: add `NoteEmbeddingEntity::class` to `@Database(entities=[…])`,
bump `version = 11`, add `.addMigrations(MIGRATION_10_11)`, expose `noteEmbeddingDao()`.

> Note: `NoteEntity.id` is `Int`; `noteId` matches that. Soft-deleted notes
> (`deletedAt != null`) and their chunks must be excluded from retrieval; hard delete
> cascades via the FK.

---

## 6. Feature Specs

### 6.1 Embeddings (the gap to close)

Two viable implementations; **decision: ship Option A (ONNX MiniLM)** for v1 because
it decouples embedding quality from whatever chat GGUF the user picks, and it is
small and fast.

- **Option A — ONNX Runtime + all-MiniLM-L6-v2 (int8), 384-dim.**
  ~23–90 MB, CPU, ~milliseconds/chunk on modern arm64. Dependency:
  `com.microsoft.onnxruntime:onnxruntime-android`. Tokenizer shipped alongside.
- **Option B — llama.cpp embedding mode.** Reuses the exact runtime we already build
  (zero new native deps), but ties embedding dim/quality to the loaded model and
  costs more RAM. Keep as fallback / for users who don't want a second model.

`EmbeddingModel` implements the ported `EmbeddingService` interface (`dimension = 384`,
L2-normalized output). The `model` string is persisted per row so a future embedder
swap can invalidate and re-index only what's stale.

### 6.2 VectorStore & Semantic Search (G1)

- **Storage:** the `note_embeddings` table above.
- **Search:** brute-force cosine similarity in Kotlin over all chunk vectors. Because
  vectors are L2-normalized, cosine reduces to a dot product. For a realistic vault
  (thousands of notes → low tens of thousands of chunks) this is a few ms and needs no
  native index. If a user vault ever exceeds ~50k chunks, revisit `sqlite-vec`.
- **Hybrid ranking:** combine vector score with the existing keyword path
  (`NoteDao.searchNotesFlow`, currently `title/content LIKE`). Final score =
  `α · cosine + (1-α) · keywordHit`. This keeps exact-term matches (names, IDs) that
  embeddings alone miss. Default `α = 0.7`.
- **Result:** ranked `NoteEntity` list with the best-matching chunk highlighted.
- **UI:** upgrade the existing search screen with a toggle (Keyword / Smart), Smart
  only shown when `AiCapability.isSupported`.

### 6.3 RAG Chat (G2)

- Retrieve top-`k` (default 6) chunks via VectorStore for the user's question.
- Assemble a grounded prompt: a system instruction ("Answer only from the provided
  notes; cite note ids; say you don't know if unsupported"), the retrieved chunks
  (each tagged with its `noteId`), then the question.
- Stream tokens from `LocalLlmProvider`. Render citations as tappable chips that open
  the source note.
- **Prompt hygiene:** reuse Hermes' `buildLocalPrompt` fix — prior turns go into the
  system/context block, only the newest user message is the user turn, so the model's
  own chat template isn't applied twice.
- New screen `AiChatScreen`; entry point from the drawer and from a note's overflow
  menu ("Ask about this note" → pre-scopes retrieval to that note + neighbors).

### 6.4 Inline Completion (G3)

- Trigger: debounce (~400 ms) after the user pauses typing, or an explicit "⇥
  complete" action in `EditorToolbar`.
- Context: current paragraph + a window of preceding note text (token-budgeted);
  optionally 1–2 retrieved chunks for cross-note continuation (off by default for
  latency).
- Output: streamed ghost text; Tab/accept inserts, Esc/keystroke dismisses.
- **Latency is the binding constraint.** Requires a small fast model (see §7) and
  cancellation on every new keystroke. Completion is the *first* feature to disable on
  mid-tier devices via `AiCapability`.

### 6.5 Agentic Filing (G4)

- Input: a new or inbox note's content + the existing folder tree
  (`NoteEntity.folderPath` / `locationPath`) and known tags (`TagEntity`).
- Prompt the local model to return **strict JSON** — `{"folder": "...", "tags": [...],
  "confidence": 0.0-1.0}` — *not* tool-calls. (Hermes' model emits text-format
  `<TOOLCALL>` tags that need special parsing; for a single classification we avoid
  that entirely by constraining output to JSON and parsing defensively.)
- The result is a **suggestion**, surfaced as an accept/edit chip in the editor. The
  user always confirms; nothing is auto-moved silently.
- Zettelkasten "inbox" flow: notes with no folder land in an Inbox view; filing runs
  on demand or on a batch "Tidy inbox" action.

### 6.6 Voice Capture (G5)

- Use Android `SpeechRecognizer` (on-device where the OEM supports it; free, no model
  to ship). Mic button in `EditorToolbar` inserts transcribed text at the cursor.
- Optional future upgrade: whisper.cpp for fully-offline transcription on devices
  lacking on-device `SpeechRecognizer`. Not required for v1.
- Requires `RECORD_AUDIO` permission, requested at point of use.

---

## 7. Model Management

Models are **downloaded on first use**, never bundled (keeps the APK small; respects
users who never enable AI).

- `ModelManager` handles: catalog, download (WorkManager + resumable), SHA-256
  verification, storage in app-private files, and delete/free-space.
- **Chat model (GGUF):** default a small instruct model, Q4_K_M. Candidates:
  Qwen2.5-1.5B-Instruct, Gemma-2-2B-it, Llama-3.2-1B/3B. ~0.8–2.0 GB download,
  ~2–3 GB peak RAM. User-selectable in settings; the app suggests one based on device
  RAM.
- **Embedding model:** all-MiniLM-L6-v2 int8 ONNX (~25–90 MB) + tokenizer.
- **First-run UX:** a clear consent + size screen ("Download <name>, ~1.2 GB, one
  time, stays on your device, works offline"). Wi-Fi-only default.
- **Integrity:** every artifact has a pinned SHA-256; downloads that fail verification
  are discarded. Model source URLs are config, over HTTPS.

---

## 8. Device Gating & Fallback

`AiCapability` decides, at runtime, whether AI features are offered:

- **ABI:** the llama.cpp build here is **`arm64-v8a` only**. `armeabi-v7a`, `x86`, and
  32-bit devices → AI **unavailable** (hidden, not broken). Octo Jotter's `minSdk` is
  **24**, so a large share of old/low-end devices simply won't see the feature.
- **RAM:** read `ActivityManager.MemoryInfo`. Tier by total RAM:
  - `< 4 GB` → AI hidden.
  - `4–6 GB` → semantic search + filing allowed; RAG chat allowed with a 1–1.5B model;
    **inline completion disabled**.
  - `≥ 6 GB` → all features; up to a 2–3B model.
- **Thermal:** subscribe to `PowerManager` thermal status; pause/throttle generation
  under `THROTTLING`+.
- **Graceful degradation:** with AI off or unsupported, search falls back to the
  existing keyword `LIKE` path, filing is manual, and the editor has no copilot — i.e.
  today's app, unchanged.

> There is currently **no cloud AI in the tree** (the README's "Firebase Gemini"
> mention is not implemented in `app/src/main`). This spec treats on-device as the
> sole AI path. If a cloud fallback is ever added, it must be **explicit opt-in** and
> clearly labeled as leaving the device — never a silent substitute for on-device.

---

## 9. Privacy & Security

- **P1** No note content, chunk, embedding, prompt, or completion leaves the device.
  The only network traffic this feature generates is **model downloads** from pinned,
  hashed URLs.
- **P2** Embeddings and chunk text live in the same encrypted-at-rest store as notes;
  respect the existing per-note `locked`/`encrypted` flags — **encrypted/locked notes
  are excluded from indexing** unless the user explicitly opts them in.
- **P3** Model files are integrity-checked (SHA-256) before load.
- **P4** AI is opt-in; enabling it shows exactly what runs locally and what downloads.
- **P5** No telemetry on AI usage content.

---

## 10. Phased Roadmap

Each phase is independently shippable and independently useful.

- **Phase 0 — Runtime port.** Create `:ondevice-llm`, vendor llama.cpp + JNI +
  `LocalLlmProvider` from Hermes, get a hello-world token stream from a downloaded
  GGUF behind a debug screen. Proves the native build on Octo Jotter's toolchain.
- **Phase 1 — Embeddings + Semantic Search (G1).** Real MiniLM embedder, `note_embeddings`
  table + migration 10→11, `NoteIndexer` WorkManager job, `VectorStore`, hybrid search
  UI. *Highest value, lowest model-size cost — ship first.*
  **Status (2026-08-09): engine done, production UI pending.** Landed: ONNX MiniLM
  embedder + WordPiece tokenizer, bag-of-words fallback, `note_embeddings` table +
  `MIGRATION_10_11`, `NoteChunker`, `VectorStore`, `NoteIndexer` + `NoteIndexingWorker`,
  hybrid `SemanticSearch` (α=0.7), `AiCapability` gating, `AiContainer` wiring, and a
  debug validation screen (`SemanticSearchDebugActivity`). 20 unit tests green. Still
  to do: download-on-first-use of the MiniLM model + `vocab.txt` (ModelManager), and
  wiring Smart search into the production search UI (the reactive `NoteViewModel` flow),
  which needs on-device validation of the ONNX embedder first.
- **Phase 2 — RAG Chat (G2).** `RagChatEngine`, `AiChatScreen`, citations.
  **Status (2026-08-09): engine + UI done; generation needs arm64 hardware to
  verify.** Landed: `RagPrompt` (grounded prompt + citations), `RagChatEngine`
  (embed → retrieve → prompt → stream `RagEvent`s), `TextGenerator` +
  `LlamaTextGenerator` (over the Phase 0 `InferenceEngine`), `AiChatViewModel` /
  `AiChatActivity` (message list, streaming, citation chips, chat-model download
  gate), a top-bar chat entry (capability-gated), and `AiContainer.ragChat()`.
  7 unit tests. Runtime-verified on an emulator up to the generation boundary
  (entry point, activity, model-gate); llama.cpp generation itself is arm64-only.
- **Phase 3 — Agentic Filing (G4)** and **Voice (G5).** Both small on top of Phases 0–1.
- **Phase 4 — Inline Completion (G3).** Last, because it's the most latency- and
  device-sensitive.

---

## 11. Risks & Open Questions

| Risk / question | Mitigation / decision needed |
|---|---|
| Native build friction on Octo Jotter's Gradle/JBR21 toolchain | Phase 0 de-risks this before any feature work; reuse Hermes' exact CMake flags |
| Model download size (1–2 GB) deters users | On-demand, Wi-Fi-default, clear consent; semantic search (Phase 1) needs only the ~25–90 MB embedder |
| Completion latency on mid-tier devices | Device gating disables it < 6 GB RAM; aggressive cancellation |
| APK/module size from native `.so` | Ship `arm64-v8a` only; models never bundled |
| Re-indexing cost on large vaults | Incremental via `contentHash`; only changed chunks re-embed; runs deferred in WorkManager |
| Vendor-copy drift from Hermes | Accept for v1; track upstream fixes manually; consider a shared published module later |
| **Open:** which default chat model? | Lean Qwen2.5-1.5B-Instruct Q4_K_M; confirm after Phase 0 benchmarks |
| **Open:** index encrypted/locked notes? | Default **exclude**; per-note opt-in. Confirm with product intent |
| **Open:** min RAM floor for *any* AI | Proposed 4 GB; validate on real low-end hardware |

---

## 12. Testing & Verification

- **Unit:** chunker boundaries, Float[]↔ByteArray packing, cosine ranking, JSON
  parsing for the classifier (malformed-output resilience), migration 10→11.
- **Instrumented:** `NoteIndexer` end-to-end on a seeded DB; VectorStore recall on a
  known query set; migration test from a v10 DB fixture.
- **Manual/device matrix:** verify gating on a `< 4 GB`, a `4–6 GB`, and a `≥ 6 GB`
  arm64 device, plus confirm the feature is fully hidden on a 32-bit / x86 emulator.
- **Privacy check:** capture network traffic during an AI session and assert the only
  requests are model downloads to the pinned hosts.

---

## Appendix A — Rocketnotes feature parity

| Rocketnotes | Octo Jotter on-device | Phase |
|---|---|---|
| Keyword search | Existing `LIKE` path | shipped |
| Semantic search | MiniLM + VectorStore | 1 |
| Chat with docs (RAG) | RagChatEngine + citations | 2 |
| Copilot completion | CompletionEngine | 4 |
| Voice-to-text | SpeechRecognizer / whisper.cpp | 3 |
| Agentic Zettelkasten filing | NoteClassifier (JSON) | 3 |
| Multi-LLM (OpenAI/Anthropic/Together) | User-selectable **local** GGUF | 0/1 |
| MCP server | Out of scope (N1) | — |
| Neovim plugin | N/A (mobile) | — |
