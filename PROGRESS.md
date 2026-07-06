# PROGRESS

## Task
Clone → rename package (`com.example` → `com.l3ad3r1.octojotter`) → add launcher icon + splash → build release APK → write README/docs.

## Done
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

## Status: v1.0 shipped; now on UI/UX design pass
v1.0 work complete. Design critique delivered as a Google Doc (23 findings:
8 High / 10 Medium / 5 Low).

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

## Signing keys (KEEP SAFE — gitignored, not in repo)
- my-upload-key.jks — alias `upload`, store/key password `octojotter`. Required
  to sign all future release updates; losing it blocks Play Store updates.
- debug.keystore — standard debug key (android/android).

## Notes
- Build env: `JAVA_HOME=/c/Program Files/Android/Android Studio/jbr`, `ANDROID_HOME` already set.
- `.env`/`debug.keystore` gitignored; secrets plugin falls back to `.env.example`.
- google-services set to WARN when `google-services.json` missing.
