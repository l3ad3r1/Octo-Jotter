# Contributing to Octo Jotter

Thanks for helping improve Octo Jotter! There are two common ways to contribute:
**community plugins** (the easiest — no build required) and **app code**.

## Contributing a plugin

Plugins are small JSON files under [`plugins/`](plugins/). They don't require
building or releasing the app — the app fetches the registry live from `main`, so
a merged plugin is installable by everyone immediately.

**Start here:**
1. Read the authoring guide: [`plugins/README.md`](plugins/README.md).
2. Copy a starter from [`plugins/_template/`](plugins/_template/) into a new
   `plugins/<your-id>/manifest.json`.
3. Register it in [`plugins/registry.json`](plugins/registry.json).
4. Open a PR using the checklist below.

### Plugin PR checklist

Copy this into your pull-request description and tick each box:

```
- [ ] New folder `plugins/<id>/` with a `manifest.json`
- [ ] `id` is unique, kebab-case, and matches the folder name
- [ ] Added a matching entry to `plugins/registry.json` (manifestUrl points at main)
- [ ] Both JSON files parse (validated locally)
- [ ] `minAppVersion` set correctly (theme 1.5 / script 1.6 / snippet 1.7 / notes.* 1.8)
- [ ] Theme only: every `onX` color is readable on its `X` (checked in light AND dark contexts)
- [ ] Script only: commands are fast + finite; only calls APIs listed in `permissions`
- [ ] `description` is a single line
- [ ] Tested in-app via Settings → Community Plugins (installed + enabled)
```

### Validate before opening the PR

```bash
python -c "import json; json.load(open('plugins/<id>/manifest.json'))"
python -c "import json; json.load(open('plugins/registry.json'))"
```

### Plugin review criteria

- **Safe** — script plugins must not request permissions they don't use. The
  runtime is sandboxed (no Java/IO/network), but keep commands well-behaved.
- **Tasteful** — themes should be legible (aim for WCAG AA contrast) and not a
  near-duplicate of an existing one.
- **Self-contained** — no external network calls or assets; everything lives in
  the manifest.

## Contributing app code

- **Build environment:** JDK 17+ (Android Studio's JBR 21 works), Android SDK
  Platform 36, AGP 9.1.1 / Gradle 9.6.1 (the wrapper handles Gradle).
- Copy `.env.example` to `.env` (see the [README](README.md#2-configure-secrets)).
- Keep changes focused; one logical change per commit with a clear message.
- Run a debug build before opening a PR:
  ```bash
  ./gradlew :app:assembleDebug
  ```
- Don't commit secrets, keystores, or `google-services.json`.

## Reporting bugs & ideas

Open an issue describing what you expected, what happened, your device/Android
version, and the app version (Settings shows the current version).
