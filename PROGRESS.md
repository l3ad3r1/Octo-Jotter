# PROGRESS

## Task
Clone → rename package (`com.example` → `com.l3ad3r1.octojotter`) → add launcher icon + splash → build release APK → write README/docs.

## Done
- **v2.5 released (2026-07-09):** versionCode 16 / versionName 2.5. Signed release APK
  (OctoJotter-v2.5.apk, v2 scheme, cert 640a69ce…) + GitHub release. Ships the Inkwell redesign.
- **Inkwell redesign (2026-07-09):** retheme to the warm cream-paper design from the
  user's Figma export. Theme layer only — Color.kt (amber-on-cream light + warm-dark
  schemes, full surface-container ramp), Theme.kt (wired new roles), Type.kt (serif
  titles / sans body / MonoFontFamily), plus a monospace markdown editor body in
  NoteApp.kt. Verified on Pixel_7 emulator (home/settings/editor/preview). System
  Serif/Monospace stand in for Libre Baskerville / JetBrains Mono (real TTFs = follow-up).
- Cloned repo, JBR 21 + Android SDK (compileSdk 36.1) confirmed.
- Generated missing Gradle wrapper; bumped to Gradle 9.6.1 (AGP 9.1.1 needs >= 9.3.1).
- Renamed package `com.example` -> `com.l3ad3r1.octojotter`; app label "Octo Jotter".
- Generated launcher icons (legacy PNG + adaptive) and splash assets from assets/logo.png.
- Wired androidx core-splashscreen: Theme.OctoJotter.Starting + installSplashScreen().
- Created release keystore (my-upload-key.jks) + debug.keystore (both gitignored).

- Built signed release APK: app/build/outputs/apk/release/app-release.apk
  (18.7 MB, v2-signed, applicationId com.l3ad3r1.octojotter, label "Octo Jotter").
- Wrote README + pushed all commits to origin/main.

- Published GitHub Release v1.0 with app-release.apk:
  https://github.com/l3ad3r1/Octo-Jotter/releases/tag/v1.0

## Status: v2.3+ (HEAD `e711b42` "Add in-app task board" is past v2.3 tag)
Plugin ecosystem shipped (Phases 1–4 + dataview API), privacy sync landed in
v2.0, in-app task board is the latest unreleased feature.

### v1.5 SHIPPED (2026-07-07) — community-plugin foundation (Phase 1)
- versionCode 6 / versionName 1.5; signed with the v1.3 key.
- Plugin platform: PluginModels (manifest+registry), PluginRepository
  (fetch/install/enable/uninstall, one active theme), data/local PluginEntity +
  PluginDao, DB v8 MIGRATION_7_8. Theme engine ThemeSpec→ColorScheme; MainActivity
  applies the enabled theme plugin. CommunityPluginsScreen (Browse/Installed);
  Settings → Community Plugins card + nav route. Samples: Ocean Dark, Rose Light.
- NOTE: minAppVersion metadata only (not enforced yet).

### v1.6 SHIPPED (2026-07-07) — Phase 2 JS scripting (Rhino)
- versionCode 7 / versionName 1.6.
- Engine: Mozilla Rhino 1.7.14 (org.mozilla:rhino), opt level -1, no native .so.
- ScriptEngine.kt: sealed safe scope + ClassShutter denying all Java classes +
  5M-instruction budget via SandboxContextFactory; Mutex-confined. Plugin API
  (JS): `octo.registerCommand(id, name, fn)` where `fn(text)->text`. Manifest
  gains `main` (inline JS) + PluginTypes.SCRIPT. Editor toolbar has a
  plugin-commands dropdown. Sample: Text Tools (UPPERCASE/lowercase/Title
  Case/Slugify/word count).

### v1.7 SHIPPED (2026-07-07) — Phase 3 (consent + snippets + minAppVersion)
- versionCode 8 / versionName 1.7.
- minAppVersion ENFORCED on install (PluginRepository.install + meetsMinVersion).
- Permission consent dialog before install (RegistryEntry.permissions) — samples
  declare none, so it's groundwork; fires for future permission-declaring plugins.
- New **snippet** plugin type: manifest snippets[] (id/name/content) inserted at
  the cursor via the editor plugin dropdown (commands+snippets unified, Bolt vs
  Bookmark icons). Sample: Markdown Snippets (callout/table/code/tasks/frontmatter).

### v1.8 SHIPPED (2026-07-07) — Phase 4 (permission-gated APIs)
- versionCode 9 / versionName 1.8.
- PluginHost bridge (createNote/listNoteTitles/log) → later: listNotes /
  searchNotes / notesWithTag / openTasks.
- PluginPermissions: notes:read, notes:write (+describe()). ScriptEngine now
  carries per-plugin GRANTED permissions; octo.notes.create [notes:write],
  octo.notes.list [notes:read], octo.log [none]. requirePermission() throws
  to JS when an ungranted API is called = REAL enforcement.
- Sample: Note Actions (script, notes:write) — "Duplicate as new note" /
  "Send tasks to new note".

### v1.9 SHIPPED (2026-07-07) — updater fix, hidden logs, nested drawer, repo discovery
- versionCode 10 / versionName 1.9.
- In-app APK downloader (OkHttp stream → filesDir/updates → FileProvider install
  intent, REQUEST_INSTALL_PACKAGES) replaces open-in-browser. Progress % + logging
  + browser fallback. DownloadStatus in VM.
- Hidden Debug Logs screen: tap "Current version" 7× (versionTaps) → nav
  "debuglogs" → DebugLogScreen reads `logcat -d` (own UID), copy/share.
- Nested drawer: NoteEntity.locationPath = repo-name + folders; buildFolderTree
  uses locationPath. Repo discovery getUserRepos with affiliation pagination.
NOTE: v1.8 users update via the OLD browser updater to GET v1.9; the in-app
downloader takes effect from v1.9 onward.

### v2.0 SHIPPED (2026-07-07) — privacy sync + app lock + note media
- versionCode 11 / versionName 2.0.
- NoteEntity gains: locked, encrypted, encryptionVersion, remoteUpdatedAt,
  lastSyncedContentHash, conflictState, conflictedRemoteContent,
  conflictedRemoteModifiedAt. AppDatabase v9 (additive).
- AppLockPreferences (DataStore) + AppLockScreen (BiometricPrompt) + ViewModel
  setAppLockEnabled / markAppUnlocked / lockApp. MainActivity routes through
  AppLockScreen when lock is enabled.
- Editor media features (NoteApp.kt + ~750 line diff) — image/paste handling,
  encryption integration.
- 1431-line privacy sync overhaul (NoteRepository) including conflict
  resolution, encrypted-content hashing, and SHA-based three-way merge.

### v2.1 SHIPPED (2026-07-07) — themes: Daybreak + Midnight OLED
- versionCode 12 / versionName 2.1.
- Daybreak: warm golden-hour light theme. Midnight OLED: true-black (#000000)
  variant of Midnight for OLED battery saving. Both fetched live from
  plugins/registry.json on main.

### v2.2 SHIPPED (2026-07-07) — themes + TOC script + CONTRIBUTING
- versionCode 13 / versionName 2.2.
- Emerald (forest-green dark), Amber (warm golden dark) themes.
- Table of Contents script plugin: scans Markdown headings and inserts a
  nested TOC at the cursor.
- CONTRIBUTING.md with a plugin PR checklist, validation steps, and review
  criteria; linked from README.

### v2.3 SHIPPED (2026-07-07) — docs + plugin authoring guide
- versionCode 14 / versionName 2.3.
- Detailed plugin authoring guide (plugins/README.md).
- Sort Tasks script plugin (octo.notes.openTasks → sorted output).
- Starter template plugin for new authors; Emerald, Amber themes already
  shipped in v2.2.
- Plugin gallery index added to the Second-Brain guide; "Add in-app task
  board" + "Add Dataview-style plugin note queries" (see below) landed
  on top of v2.3 and are NOT YET released.

### v2.4 SHIPPED (2026-07-07) — Dataview queries + in-app Task Board
- versionCode 15 / versionName 2.4. Signed with the v1.3+ upload key
  (cert SHA-256 640a69ce...) so v2.3 installs update in place.
- Release: https://github.com/l3ad3r1/Octo-Jotter/releases/tag/v2.4
  (asset: OctoJotter-v2.4.apk, 20.3 MB, marked Latest).
- Rolls up the two unreleased features that landed on top of v2.3:
- **Dataview-style plugin note queries** (`cf95eb1`): PluginHost extended
  with listNotes() / searchNotes(q) / notesWithTag(tag) / openTasks(); all
  gated on notes:read. ScriptEngine grants the new APIs per permission.
  New sample plugin `task-dashboard` uses these to power a custom view.
  docs/SECOND-BRAIN.md describes the query API.
- **In-app task board** (`e711b42`): Built-in screen that aggregates all
  open `- [ ]` items from notes (parseTaskBoardItems / TaskBoardItem),
  two columns (Open / Done, sorted by lastModifiedLocally), tap-to-toggle
  via setTaskChecked(noteId, lineNumber, checked). Reachable from the
  notes-list top bar; documented in docs/SECOND-BRAIN.md.

### Untracked junk (not part of repo)
- `OctoJotter-v2.1.apk`, `OctoJotter-v2.2.apk`, `OctoJotter-v2.3.apk` —
  downloaded release assets, ignored.
- `Polsia-Ai/` — separate project (own PROGRESS.md / SKILL.md / agents
  / scripts / state). Dropped in this checkout; do NOT commit or move.

### Design improvements in progress (against the critique)
- DONE Phase 1: octopus-blue Material 3 theme (light + dark) in Color.kt/Theme.kt;
  dynamicColor default false; fixed "Octojot" -> "Octo Jotter" title. (review #1)
- DONE Phase 2: swipe-to-delete + Undo Snackbar (pending-deletion pattern in
  NoteViewModel); removed redundant card trash icon; restored 48dp touch targets
  on pin / drawer-folder-delete / add-tag buttons. (review #3, #4, #5)
- DONE Phase 3: semantic status colors via OctoStatusColors CompositionLocal
  (light/dark, WCAG AA); offline = neutral; preview headings onSurface. (#2 + mediums)
- DONE Phase 4a: editor IME-aware scrollable toolbar + H1/code/checkbox/wiki-link;
  tag chips remove only via ✕; preview renders - [ ] / - [x]. (#7, #8, md coverage)
- DONE Phase 4b: Settings retitled; full-width Save + guarded "Disconnect GitHub";
  Close (not trash) dismiss; empty state = "Create your first note"; editor app bar
  matches others; relative timestamps on cards. (token safety + mediums/low)
- Verified: assembleDebug green after every batch.

- DONE Phase 5: removed bottom nav (Settings = pushed screen w/ back arrow);
  fixed markdown italic-vs-bold regex; remember()+cache MarkdownVisualTransformation. (#6, md perf)
- DONE Phase 6: DB export share sheet via FileProvider + ACTION_SEND. (md export)
- DONE Phase 7: scrollable sort/view row (no overflow) + "/" folder separator. (md/low)

- DONE Phase 8 (final polish): editor app bar shows note title; icon-only sync
  badge; tags capped at 3; folder groups are flat sections (no cards-in-cards).
- DONE in-app updater: Settings > Updates card checks GitHub Releases latest,
  compares to BuildConfig.VERSION_NAME, offers APK download. (GithubApiService
  getLatestRelease + NoteViewModel.checkForUpdate + UpdateStatus)

### Sync diagnosis (user report: can't pull Dronehire/Cane-Theory second-brain)
- ROOT CAUSE: those are private REPOSITORIES, not Gists. The app only uses the
  /gists API (no repo support), and the pull filter only imports gists containing
  a .md file. The account's only gists are hermes-backup.json (no .md) -> nothing imports.
- To pull repos we'd need a NEW feature: GitHub Contents/Trees API + repo-picker UI
  + folder mapping + PAT `repo` scope. Awaiting owner decision on scope.

### v1.1 SHIPPED (2026-07-06)
- versionCode 2 / versionName 1.1; signed release APK published:
  https://github.com/l3ad3r1/Octo-Jotter/releases/tag/v1.1
- In-app updater verified: installed v1.0 now sees v1.1; shipped v1.1 reports up-to-date.

### Repo-sync feature: BUILT (2026-07-06) — compiles clean
History: `main` was rolled back to the clean v1.1 base (old Backup, 2612b37); the
discarded commits had regressed the package back to `com.example`. Old main
recoverable at SHA 7728fb4 until GC. Then built two-way GitHub repository sync:
- NoteEntity: repository/path/sha columns; AppDatabase v7 + additive MIGRATION_6_7.
- NoteDao: gist sync scoped to `repository IS NULL`; getNotesToSyncForRepository +
  getNoteByRepoAndPath.
- GithubApiService: getGitTree / getGitBlob / createOrUpdateFile (PUT) /
  deleteRepoFile (DELETE+body), path-segment encoded.
- RepoPreferences DataStore: repo list + selected; defaults FIXED to l3ad3r1/*
  (renjacob10000/* was the root-cause 404). PAT needs `repo` scope (repos private).
- NoteRepository: pullFromRepository (main->master fallback, .md by blob sha,
  keeps dirty notes) / pushToRepository (conflict-safe sha + 409/422 retry) /
  deleteNoteFromRepository.
- NoteViewModel: repositories/selectedRepository state + syncRepositoryNow().
- Settings: Repository Sync card (chips to select, add/remove, Sync button).
DESIGN NOTE: repo push/pull is MANUAL only; background auto-sync stays gist-only
so real knowledge-base repos aren't auto-committed on every edit.
VERIFIED: `:app:assembleDebug` — kspDebugKotlin (Room v7 migration + Moshi) clean,
zero compile errors. (debug.keystore is gitignored; regenerate via keytool if absent.)
NEXT: on-device test pull of l3ad3r1/Dronehire-second-brain with a repo-scoped PAT;
then decide on optional background repo sync + version/release bump.

### v1.3 SHIPPED (2026-07-07) — repo sync + Settings cleanup
- versionCode 4 / versionName 1.3; signed release published as Latest:
  https://github.com/l3ad3r1/Octo-Jotter/releases/tag/v1.3 (OctoJotter-v1.3.apk).
- Stale v1.2 release+tag (from discarded regressed code) deleted.
- Ships: two-way repo sync; single unified Sync Now (gists + selected repo);
  dark-mode toggle; removed PAT how-to.
- ⚠️ SIGNED WITH A NEW KEY (see below) — cannot update older installs in place;
  reinstall fresh after exporting notes.

### v1.4 SHIPPED (2026-07-07) — discovery + tree + formatting + back-to-exit
- versionCode 5 / versionName 1.4; signed with the SAME v1.3 key (updates in
  place, no reinstall). Latest: https://github.com/l3ad3r1/Octo-Jotter/releases/tag/v1.4
- Repo discovery (getUserRepos → Settings "Find my GitHub repositories", tap-to-add).
- Nested folder tree (buildFolderTree/flattenFolderTree; recursive expand/collapse
  + counts) replaces flat full-path accordions in the Grouped view.
- Editor: H2/H3/numbered/quote/link buttons; fixed insertMarkdown line-prefix
  mirroring (trailing-space = line prefix, no suffix).
- Home: double-back-to-exit with toast (first back closes drawer).

### Community plugins — FOUNDATION built (2026-07-07), NOT yet released
Decision: **phased hybrid**. Build the platform once; phase 1 = declarative
plugins (themes), phase 2 (later) = QuickJS scripting runtime + permission model.
Registry lives IN this repo: plugins/registry.json + plugins/<id>/manifest.json,
fetched via raw.githubusercontent (public, no token). Built so far:
- plugin/PluginModels (manifest+registry), PluginRepository (fetch/install/enable/
  uninstall; one active theme), data/local PluginEntity+PluginDao, DB v8 MIGRATION_7_8.
- Theme engine: ThemeSpec→ColorScheme (ui/theme/PluginTheme.kt); MyApplicationTheme
  gained overrideColorScheme; MainActivity applies the enabled theme plugin.
- NoteViewModel plugin state/actions; ui/CommunityPluginsScreen (Browse/Installed);
  Settings → Community Plugins card + "plugins" nav route.
- Sample plugins live: Ocean Dark, Rose Light (theme type). Verified assembleDebug.
NOTE: minAppVersion in manifests is metadata only (not yet enforced on install).

### Community plugins PHASE 2 built (2026-07-07) — JS scripting
Engine choice: **Mozilla Rhino** (org.mozilla:rhino:1.7.14), pure-JVM/interpreted
(opt level -1) — NOT QuickJS, to avoid a native .so destabilizing the build.
- plugin/ScriptEngine.kt: sealed safe scope + ClassShutter denying ALL Java classes
  + instruction budget (5M) via SandboxContextFactory; Mutex-confined.
- Plugin API (JS): `octo.registerCommand(id, name, fn)` where `fn(text)->text`.
  Manifest gains `main` (inline JS) + PluginTypes.SCRIPT.
- VM reloads engine when enabled script plugins change; pluginCommands +
  runPluginCommand. Editor toolbar has a plugin-commands dropdown (runs on note text).
- Sample: Text Tools (UPPERCASE/lowercase/Title Case/Slugify/word count).
- VERIFIED the JS in real Rhino via shell (all commands correct). assembleDebug green.
### Community plugins PHASE 3 built (2026-07-07)
- minAppVersion ENFORCED on install (PluginRepository.install(entry, appVersion)
  + meetsMinVersion; blocks with a "please update" message).
- Permission consent dialog before installing plugins that declare permissions
  (RegistryEntry.permissions). NOTE: samples declare none, so it's groundwork —
  fires for any future permission-declaring plugin; no API enforces perms yet.
- New declarative **snippet** plugin type: manifest snippets[] (id/name/content)
  inserted at the cursor via the editor plugin dropdown (commands+snippets unified,
  Bolt vs Bookmark icons). Sample: Markdown Snippets (callout/table/code/tasks/frontmatter).
- Registry now exercises all 4 types: theme, script, snippet.
### Community plugins PHASE 4 built (2026-07-07) — permission-gated APIs
- PluginHost bridge (createNote/listNoteTitles/log) implemented in NoteViewModel
  (runBlocking → repository). PluginPermissions: notes:read, notes:write (+describe()).
- ScriptEngine now carries per-plugin GRANTED permissions (PluginSpec); exposes
  octo.notes.create [notes:write], octo.notes.list [notes:read], octo.log [none];
  requirePermission() throws to JS when a plugin calls an ungranted API = REAL enforcement.
- Consent dialog shows human-readable descriptions. Sample: Note Actions (script,
  notes:write) — "Duplicate as new note" / "Send tasks to new note". Verified in Rhino.
NEXT: more APIs (clipboard, note read-content, settings), md post-processors /
custom toolbar buttons; scriptUrl fetch; plugin updates; per-permission partial grant.

### v1.9 (2026-07-07) — updater fix, hidden logs, nested drawer, repo discovery
- In-app APK downloader (OkHttp stream → filesDir/updates → FileProvider install
  intent, REQUEST_INSTALL_PACKAGES) replaces open-in-browser (which stalled on GH
  asset redirects). Progress % + logging + browser fallback. DownloadStatus in VM.
- Hidden Debug Logs screen: tap "Current version" 7× (versionTaps) → nav "debuglogs"
  → DebugLogScreen reads `logcat -d` (own UID), copy/share. Updater logs via tag "OctoJotter".
- Nested drawer: NoteEntity.locationPath = repo-name + folders; buildFolderTree now
  uses locationPath (drawer + grouped list nest by repo); selectedFolder is a full
  PATH now; filteredNotes matches path prefix. Drawer renders expand/collapse tree
  with counts; empty custom folders merged as top-level (deletable).
- Repo discovery: getUserRepos affiliation owner→owner,collaborator,organization_member
  + pagination (MAX_REPO_PAGES=20) in listAccessibleRepositories. Fixes missing repos.
NOTE: v1.8 users update via the OLD browser updater to GET v1.9; the in-app downloader
takes effect from v1.9 onward.

## Signing keys (KEEP SAFE — gitignored, not in repo)
- ⚠️ ORIGINAL my-upload-key.jks WAS LOST (not on this machine as of 2026-07-07).
  REGENERATED a new upload key 2026-07-07: alias `upload`, store/key password
  `octojotter`, at repo root my-upload-key.jks. Cert SHA-256:
  640a69ce998145319cc5094da93667fc804a19d7909f80eb0a83574101f17c5f.
  This is now THE key for all future updates — BACK IT UP. Installs signed with
  the old key (v1.0–v1.2) require uninstall+reinstall to move to v1.3+.
- debug.keystore — standard debug key (android/android); also regenerated
  2026-07-07 (was likewise absent from the fresh clone).

## Notes
- Build env: `JAVA_HOME=/c/Program Files/Android/Android Studio/jbr`, `ANDROID_HOME` already set.
- `.env`/`debug.keystore` gitignored; secrets plugin falls back to `.env.example`.
- google-services set to WARN when `google-services.json` missing.
