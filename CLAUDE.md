# CLAUDE.md

Follow `E:\claude-projects\CLAUDE.md` for shared working rules. This file contains only project facts.

## Project

Octo Jotter is a Markdown and Gist notes Android app. Package `com.l3ad3r1.octojotter`; Gradle 9.6.1; JBR 21.

- Build: `./gradlew assembleGithubDebug`. Two distribution flavours: `github`
  (self-updating, holds `REQUEST_INSTALL_PACKAGES`) and `play` (neither — Play
  forbids self-install). Play uploads use `bundlePlayRelease`.
- Release builds run R8; reflective libraries need keep rules in `app/proguard-rules.pro`.
- Room: schema is at v13. Destructive migration is scoped to schema versions 1-5
  only. A missing migration for v6+ now throws instead of wiping notes — add the
  migration. `DatabaseMigrationTest` exercises the upgrade path on Robolectric;
  it only reaches back to v11 because `schemas/` starts at `11.json`. Room finds
  those JSONs through `sourceSets["debug"].assets`, which is why the migration
  test needs no device and why schemas never reach a release APK.
- App lock uses `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` so a device with no
  fingerprint reader unlocks with its pattern/PIN/password. Do **not** "upgrade"
  it to `BIOMETRIC_STRONG`: androidx.biometric rejects that combination on API
  28-29, which with minSdk 24 is a hard lockout on Android 9/10.
  `AppLockAuthenticatorTest` fails if anyone tries.
- Trash is local and reversible; `pendingRemoteDelete` means "emptied, remote
  copy still to delete". Rows survive until `processPendingRemoteDeletes()`
  confirms the Gist/repo file is gone — dropping one early is what used to make
  deleted notes reappear on the next pull.
- Artifact signing uses the gitignored upload keystore `my-upload-key.jks`; never delete or move it. Its password is in the gitignored `keystore.properties` at the repo root — read that file, don't ask for it. Keep the password out of this file and every other committed file: **this repo is public**. See "Release signing" in `README.md`.
- `new-upload-key.jks` is a stray key (password lost) that signed v2.6 only. The upload key is `my-upload-key.jks`, cert SHA-256 `640a69ce…f17c5f`. Don't sign with the stray one.
- Keep `OctoJotter-vX.Y.apk` artifacts at the repository root.
- Plugin direction: declarative theme plugins now and QuickJS scripting later; the community registry lives under `plugins/`.
