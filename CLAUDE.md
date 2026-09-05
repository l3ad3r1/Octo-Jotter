# CLAUDE.md

Follow `E:\claude-projects\CLAUDE.md` for shared working rules. This file contains only project facts.

## Project

Octo Jotter is a Markdown and Gist notes Android app. Package `com.l3ad3r1.octojotter`; Gradle 9.6.1; JBR 21.

- Build: `./gradlew assembleDebug`.
- Artifact signing uses the gitignored upload keystore `my-upload-key.jks`; never delete or move it. Its password is in the gitignored `keystore.properties` at the repo root — read that file, don't ask for it. Keep the password out of this file and every other committed file: **this repo is public**. See "Release signing" in `README.md`.
- `new-upload-key.jks` is a stray key (password lost) that signed v2.6 only. The upload key is `my-upload-key.jks`, cert SHA-256 `640a69ce…f17c5f`. Don't sign with the stray one.
- Keep `OctoJotter-vX.Y.apk` artifacts at the repository root.
- Plugin direction: declarative theme plugins now and QuickJS scripting later; the community registry lives under `plugins/`.
