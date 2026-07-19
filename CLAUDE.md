# CLAUDE.md

Follow `E:\claude-projects\CLAUDE.md` for shared working rules. This file contains only project facts.

## Project

Octo Jotter is a Markdown and Gist notes Android app. Package `com.l3ad3r1.octojotter`; Gradle 9.6.1; JBR 21.

- Build: `./gradlew assembleDebug`.
- Artifact signing uses the gitignored upload keystore; never delete or move it, and keep its password out of this file.
- Keep `OctoJotter-vX.Y.apk` artifacts at the repository root.
- Plugin direction: declarative theme plugins now and QuickJS scripting later; the community registry lives under `plugins/`.
