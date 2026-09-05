<div align="center">

<img src="assets/ic_launcher-web-512.png" width="128" alt="Octo Jotter logo" />

# Octo Jotter

**An offline-first Markdown notes app for Android that syncs to your GitHub Gists.**

</div>

---

Octo Jotter keeps your notes on-device and in sync with private GitHub Gists, so
your writing is versioned, portable, and never locked into a proprietary cloud.
Write in Markdown, preview instantly, and pick up on any machine that can read a
Gist.

## Features

- 📝 **Markdown editor** with a grouped formatting toolbar — bold, italic,
  strikethrough, super/subscript, highlight, headings, bullet/numbered/task
  lists, callouts, quotes, inline code and code blocks, links, images,
  drawings, tables, dividers, indent/outdent and undo/redo — plus adjustable
  editor type size, live word count, and a preview that renders all of it.
- 🐙 **GitHub Gist sync** — each note is a **private** Gist, synced in the
  background. Your data stays in your own GitHub account.
- 📴 **Offline-first** — notes live in a local Room database and sync when a
  network is available.
- 🗂️ **Folders & tags** for organising notes, plus **pin**, **search**, and
  swipe-to-delete.
- 💾 **Auto-saved drafts** so nothing is lost mid-edit.
- 🌗 **Light / dark / system** theming, plus **community theme/snippet/script
  plugins** (see [Community plugins](#community-plugins)).
- 🔒 **Encrypted token storage** — your GitHub Personal Access Token is stored
  with `androidx.security.crypto`, never in plain text.
- ✨ **AI assistance** powered by Gemini (via Firebase AI) — optional, requires
  Firebase configuration (see below).

## Tech stack

| Area | Choice |
|------|--------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3, Navigation Compose |
| Local storage | Room, DataStore Preferences |
| Background work | WorkManager |
| Networking | Retrofit + Moshi + OkHttp (GitHub Gists API) |
| AI | Firebase AI (Gemini) + App Check |
| Build | AGP 9.1.1, Gradle 9.6.1, KSP |

**Min SDK 24 (Android 7.0) · Target/Compile SDK 36 · `applicationId` `com.l3ad3r1.octojotter`**

## Getting started

### Prerequisites

- Android Studio (latest) or the Android command-line SDK, with **SDK Platform 36**.
- **JDK 17+** (Android Studio's bundled JBR 21 works out of the box).

### 1. Clone

```bash
git clone https://github.com/l3ad3r1/Octo-Jotter.git
cd Octo-Jotter
```

### 2. Configure secrets

Secrets are read from a `.env` file via the Secrets Gradle Plugin (falling back to
`.env.example`). Copy the example and fill in your key:

```bash
cp .env.example .env
```

```properties
# .env
GEMINI_API_KEY=your_key_here
```

> The AI features additionally require **Firebase**. Add your own
> `google-services.json` to `app/`. Without it the build still succeeds
> (`google-services` is set to `WARN`), but AI/App Check calls won't function.

### 3. Run

```bash
./gradlew installGithubDebug   # build + install a debug build on a connected device
# or open the project in Android Studio and press Run
```

The app builds in two distribution flavours. They differ in one thing: whether
the app may update itself.

| Flavour | Self-update | Use |
|---------|-------------|-----|
| `github` | yes — downloads the release APK and opens the installer | GitHub release builds |
| `play`   | no — no updater UI, no `REQUEST_INSTALL_PACKAGES` | Play Store submissions |

Play's Device and Network Abuse policy forbids an app it distributes from
installing an APK itself, so a Play upload **must** use the `play` flavour.

On first launch, open **Settings → GitHub** and paste a **Personal Access Token**
with the `gist` scope to enable sync. Create one at
<https://github.com/settings/tokens>.

## Building a release APK

The release build type is signed with an upload keystore supplied via environment
variables (never commit your keystore):

```bash
./gradlew assembleGithubRelease   # GitHub release APK (self-updating)
./gradlew bundlePlayRelease       # Play Store bundle (no self-update)
```

Outputs land in `app/build/outputs/apk/github/release/` and
`app/build/outputs/bundle/playRelease/` respectively. Release builds run R8
(shrinking, resource shrinking and obfuscation); keep `proguard-rules.pro` in
mind when adding a library that resolves anything reflectively.

To generate an upload keystore:

```bash
keytool -genkeypair -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias upload
```

## Community plugins

Octo Jotter can be extended with lightweight, JSON-based plugins — **themes**,
editor **snippets**, and sandboxed **script** commands. Plugins are hosted in this
repo under [`plugins/`](plugins/) and installed in-app from **Settings → Community
Plugins** (no app rebuild required — the registry is fetched live).

- 📖 **Authoring guide:** [`plugins/README.md`](plugins/README.md) — full manifest
  schema, the theme color slots, the sandboxed `octo` script API, and a
  test/submit checklist.
- 🧩 **Starter template:** copy [`plugins/_template/`](plugins/_template/) for a
  ready-made `theme` / `snippet` / `script` manifest to fill in.
- 🤝 **Submitting one:** see [`CONTRIBUTING.md`](CONTRIBUTING.md) for the plugin PR
  checklist and review criteria.

### Use it as a Second Brain

With repository sync + the **Second Brain Templates** and **Second Brain Tools**
plugins, Octo Jotter becomes a Git-backed knowledge vault (daily notes, projects,
learnings, tasks). See the [Second Brain guide](docs/SECOND-BRAIN.md) for the
vault structure and how it maps to a typical Obsidian setup.

## Project structure

```
app/src/main/java/com/l3ad3r1/octojotter/
├── MainActivity.kt          # single-Activity host, installs the splash screen
├── ui/                      # Compose screens, NoteApp, NoteViewModel, theme
│   └── editor/              # formatting toolbar + pure Markdown transforms
├── data/
│   ├── local/               # Room entities, DAO, DataStore prefs, backup
│   ├── remote/              # GitHub Gists API (Retrofit), encrypted TokenManager
│   └── repository/          # NoteRepository — local <-> Gist reconciliation
└── sync/                    # SyncWorker (WorkManager)
```

## The app icon

The launcher icon is imported, not drawn by hand each time. `assets/icon-source.png`
is the source artwork (a mockup with a white background and a reference border);
`tools/generate_icon.py` isolates the line art from that border, keys white to
transparent, and emits the adaptive foreground + monochrome layers plus every
legacy/splash/store raster density from the result:

```bash
python tools/generate_icon.py     # requires Pillow, numpy, scipy
```

To restyle the icon, replace `assets/icon-source.png` with new artwork (same
mockup shape: white background, octopus line art) and re-run the script; don't
hand-edit anything under `res/mipmap-*/ic_launcher*.png`.

## Release signing

**The upload key is `my-upload-key.jks` in the repo root** (gitignored, along with
every `*.jks`). Its credentials live in **`keystore.properties`** in the repo root —
also gitignored, and the first place to look if a build asks for a password:

```properties
storeFile=my-upload-key.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

`app/build.gradle.kts` reads that file automatically, so `./gradlew assembleRelease`
works with no environment setup. `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_PASSWORD` /
`KEY_ALIAS` environment variables still override it, which is what CI uses.

Verify you have the right key before publishing — this fingerprint is the app's
identity on Play, and signing an update with anything else makes it un-installable
over an existing install:

```bash
keytool -list -v -keystore my-upload-key.jks -storepass "$STORE_PASSWORD" | grep SHA256
# SHA256: 64:0A:69:CE:99:81:45:31:9C:C5:09:4D:A9:36:67:FC:80:4A:19:D7:90:9F:80:EB:0A:83:57:41:01:F1:7C:5F
```

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

> ⚠️ **`new-upload-key.jks` (repo root) is NOT the upload key.** It's a stray key whose
> password is lost; it signed `OctoJotter-v2.6.apk` only (cert `33b83ca0…`). Everything
> from v2.7 on uses `my-upload-key.jks` (`640a69ce…`). Don't delete either file, but
> never sign with the stray one.
>
> ⚠️ Keys and passwords never go in this file or any other committed file — **this
> repository is public.**

## License

[MIT](LICENSE). Contributions — including community plugins — are accepted under
the same terms.

Bundled third-party code keeps its own licence: `ondevice-llm` vendors
[llama.cpp](https://github.com/ggerganov/llama.cpp) (MIT).
