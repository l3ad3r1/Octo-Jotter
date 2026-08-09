package com.l3ad3r1.octojotter.ai.chat

import com.l3ad3r1.octojotter.ai.index.ChunkHit

/** A note offered as a source for an answer (rendered as a tappable chip). */
data class Citation(
    val noteId: Int,
    val snippet: String,
    val score: Float,
)

/** Grounding retrieved for a question plus the assembled system prompt. */
data class RagContext(
    val system: String,
    val question: String,
    val citations: List<Citation>,
) {
    val hasGrounding: Boolean get() = citations.isNotEmpty()
}

/** A streamed event from a RAG answer. */
sealed interface RagEvent {
    /** Emitted first: the notes retrieved as grounding. */
    data class Sources(val citations: List<Citation>) : RagEvent
    /** A generated token/text fragment. */
    data class Token(val text: String) : RagEvent
    /** Generation finished normally. */
    data object Done : RagEvent
    /** Generation failed. */
    data class Error(val message: String) : RagEvent
}

/**
 * Pure, testable grounded-prompt assembly (spec §6.3 of docs/ON-DEVICE-AI.md).
 *
 * Builds a system prompt that constrains the model to answer only from the
 * retrieved note chunks and to cite them as `[Note <id>]`. Chunks are deduped to
 * the best one per note and packed under a character budget so the context fits
 * the model's window.
 */
object RagPrompt {

    private val SYSTEM_HEADER =
        """
        You are a helpful assistant answering questions about the user's personal notes.
        Use ONLY the information in the notes provided below. When you use a note, cite it
        as [Note <id>]. If the notes do not contain the answer, say you don't know rather
        than inventing facts.
        """.trimIndent()

    private const val NO_CONTEXT =
        "No relevant notes were found. Tell the user you couldn't find anything in their notes about this."

    fun build(question: String, chunks: List<ChunkHit>, maxChars: Int = 6000): RagContext {
        // Best chunk per note, preserving rank order.
        val bestPerNote = LinkedHashMap<Int, ChunkHit>()
        for (c in chunks.sortedByDescending { it.score }) {
            val existing = bestPerNote[c.noteId]
            if (existing == null || c.score > existing.score) bestPerNote[c.noteId] = c
        }

        val included = mutableListOf<ChunkHit>()
        val block = StringBuilder()
        for (c in bestPerNote.values) {
            val entry = "[Note ${c.noteId}] ${c.chunkText.trim()}"
            if (block.isNotEmpty() && block.length + entry.length + 2 > maxChars) break
            if (block.isNotEmpty()) block.append("\n\n")
            block.append(entry)
            included += c
        }

        val system = if (included.isEmpty()) {
            "$SYSTEM_HEADER\n\n$NO_CONTEXT"
        } else {
            "$SYSTEM_HEADER\n\nNotes:\n$block"
        }

        val citations = included.map {
            Citation(noteId = it.noteId, snippet = snippet(it.chunkText), score = it.score)
        }
        return RagContext(system = system, question = question.trim(), citations = citations)
    }

    private fun snippet(text: String, max: Int = 160): String {
        val t = text.trim().replace(Regex("\\s+"), " ")
        return if (t.length <= max) t else t.take(max).trimEnd() + "…"
    }
}
