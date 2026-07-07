# Creating Octo Jotter Plugins

Octo Jotter has a lightweight community-plugin system. A plugin is just a small
**JSON file** hosted in this repo — no compiling, no APK, no app update required.
The app fetches the registry live, so a merged plugin is installable by everyone
the moment it lands on `main`.

There are three kinds of plugin:

| Type | What it does | Needs code? |
|------|--------------|-------------|
| **`theme`** | Re-colors the whole app (light or dark palette) | No — just colors |
| **`snippet`** | Adds insertable Markdown templates to the editor | No — just text |
| **`script`** | Adds editor commands that transform text / create notes | Yes — a little JavaScript |

---

## Available plugins

The plugins currently in this registry — browse their manifests for reference.

### Themes
| Plugin | Mode | Description |
|--------|------|-------------|
| [Ocean Dark](ocean-dark/manifest.json) | dark | Deep-blue dark theme with aqua accents. |
| [Midnight](midnight/manifest.json) | dark | Near-black midnight with soft periwinkle/indigo accents. |
| [Midnight OLED](midnight-oled/manifest.json) | dark | True-black (`#000000`) variant of Midnight for OLED battery saving. |
| [Emerald](emerald-dark/manifest.json) | dark | Deep forest-green with bright emerald accents. |
| [Amber](amber-dark/manifest.json) | dark | Warm dark lit by golden amber accents. |
| [Rose Light](rose-light/manifest.json) | light | Warm light theme with rose accents. |
| [Daybreak](daybreak/manifest.json) | light | Warm golden-hour light theme; sunrise companion to Midnight. |

### Scripts
| Plugin | Permissions | Description |
|--------|-------------|-------------|
| [Text Tools](text-tools/manifest.json) | — | UPPERCASE, lowercase, Title Case, Slugify, word count. |
| [Table of Contents](toc/manifest.json) | — | Scans headings and inserts a nested TOC at the top. |
| [Sort Tasks](sort-tasks/manifest.json) | — | Moves completed `- [x]` items to the bottom of the note. |
| [Note Actions](note-actions/manifest.json) | `notes:write` | Duplicate a note, or split its tasks into a new note. |

### Snippets
| Plugin | Description |
|--------|-------------|
| [Markdown Snippets](md-snippets/manifest.json) | Callout, table, code block, task list, front-matter templates. |

---

## 1. How the system works

1. **The registry** — [`plugins/registry.json`](registry.json) is a single index the
   app downloads from
   `https://raw.githubusercontent.com/l3ad3r1/Octo-Jotter/main/plugins/registry.json`.
   Each entry points at one plugin's `manifest.json` by raw URL.
2. **Install** — In the app: **Settings → Community Plugins** (pull to refresh).
   Tapping *Install* downloads the manifest and stores it locally, so the plugin
   keeps working offline. Installed plugins start **disabled**.
3. **Enable** — The user toggles a plugin on. Only **one theme** can be enabled at
   a time; any number of script/snippet plugins can be enabled together.
4. **Version gate** — If a manifest sets `minAppVersion`, the app refuses to
   install it on an older build and tells the user to update.

Because everything is fetched from `main`, **you don't rebuild or release the app
to ship a plugin** — you just add files under `plugins/` and push (or open a PR).

---

## 2. Anatomy of a plugin

Every plugin is a folder under `plugins/` containing a single `manifest.json`:

```
plugins/
  my-plugin/
    manifest.json
```

…plus one entry in [`plugins/registry.json`](registry.json) so the app can find it.

### Manifest fields (all types)

| Field | Required | Notes |
|-------|----------|-------|
| `id` | ✅ | Unique, kebab-case (e.g. `midnight`). Must match the folder name. |
| `name` | ✅ | Display name shown in the plugin browser. |
| `version` | ✅ | Semver string, e.g. `"1.0.0"`. |
| `type` | ✅ | `"theme"`, `"snippet"`, or `"script"`. |
| `author` | ➖ | Your GitHub handle. |
| `description` | ➖ | One line; shown in the browser and before install. |
| `minAppVersion` | ➖ | Lowest app version that supports it (see per-type minimums below). |
| `permissions` | ➖ | Array of permission strings (script plugins only). |
| `theme` | theme | The color spec (see §3). |
| `snippets` | snippet | Array of templates (see §4). |
| `main` | script | Inline JavaScript source (see §5). |

---

## 3. Theme plugins (`type: "theme"`)

A theme overrides Material 3 color slots. **`minAppVersion`: `"1.5"`.**

```json
{
  "id": "my-theme",
  "name": "My Theme",
  "version": "1.0.0",
  "minAppVersion": "1.5",
  "author": "you",
  "description": "A short, punchy description.",
  "type": "theme",
  "permissions": [],
  "theme": {
    "dark": true,
    "colors": {
      "primary": "#AEB7FF",
      "onPrimary": "#141C51",
      "background": "#05060F",
      "onBackground": "#E4E1F0",
      "surface": "#090B16",
      "onSurface": "#E4E1F0"
    }
  }
}
```

- **`dark`** — `true` picks the dark base scheme, `false` the light one. Any slot
  you don't specify falls back to that base, so you can override just a few colors.
- **`colors`** — a map of slot → hex. Accepted formats: `#RRGGBB` or `#AARRGGBB`.

### Supported color slots

```
primary            onPrimary            primaryContainer    onPrimaryContainer
secondary          onSecondary          secondaryContainer  onSecondaryContainer
tertiary           onTertiary           tertiaryContainer   onTertiaryContainer
background         onBackground
surface            onSurface            surfaceVariant      onSurfaceVariant
surfaceContainer
error              onError              errorContainer      onErrorContainer
outline            outlineVariant
```

**Tips**
- Each `onX` color must be readable **on top of** its `X` color — aim for WCAG AA
  (≥ 4.5:1 for body text). Test in the app before submitting.
- `surface`/`background` are the app's canvas; `surfaceContainer` is used for cards
  and elevated areas — make it slightly distinct from `surface`.
- For a true-black OLED theme, set `background` and `surface` to `#000000` and use a
  near-black `surfaceContainer` (e.g. `#0A0A0F`) so cards are still distinguishable.
- Real examples: [`midnight`](midnight/manifest.json),
  [`midnight-oled`](midnight-oled/manifest.json), [`daybreak`](daybreak/manifest.json),
  [`ocean-dark`](ocean-dark/manifest.json), [`rose-light`](rose-light/manifest.json).

---

## 4. Snippet plugins (`type: "snippet"`)

Snippets are named text templates inserted at the cursor in the editor.
**`minAppVersion`: `"1.7"`.** No permissions or code needed.

```json
{
  "id": "my-snippets",
  "name": "My Snippets",
  "version": "1.0.0",
  "minAppVersion": "1.7",
  "author": "you",
  "type": "snippet",
  "snippets": [
    { "id": "callout", "name": "Callout (note)", "content": "> [!note]\n> " },
    { "id": "table",    "name": "Table (2x2)",   "content": "| A | B |\n| --- | --- |\n| a | b |\n" },
    { "id": "tasks",    "name": "Task list",     "content": "- [ ] \n- [ ] \n- [ ] \n" }
  ]
}
```

- Each snippet has `id`, `name`, and `content`.
- `content` is inserted verbatim — use `\n` for newlines. Markdown is fine.
- Full example: [`md-snippets`](md-snippets/manifest.json).

---

## 5. Script plugins (`type: "script"`)

Script plugins register **editor commands** written in JavaScript.
**`minAppVersion`: `"1.6"`** (or `"1.8"` if you use the `octo.notes.*` API).

The JS goes in the `main` field as a **JSON-escaped string** (escape `"` as `\"`
and newlines as `\n`).

```json
{
  "id": "my-tools",
  "name": "My Tools",
  "version": "1.0.0",
  "minAppVersion": "1.6",
  "author": "you",
  "type": "script",
  "permissions": [],
  "main": "octo.registerCommand(\"upper\", \"UPPERCASE\", function(t){ return String(t).toUpperCase(); });"
}
```

### The `octo` API

Your script runs once at load and registers commands. A command receives the
current editor text and returns the replacement text.

| Call | Permission | Description |
|------|-----------|-------------|
| `octo.registerCommand(id, name, fn)` | — | Register an editor command. `fn(text)` returns the new text. |
| `octo.log(message)` | — | Write to the app's debug log (Settings → tap version 7×). |
| `octo.notes.list()` | `notes:read` | Returns an array of your note titles. |
| `octo.notes.create(title, content)` | `notes:write` | Creates a new note. |

Declare any API you use in `permissions` (e.g. `"permissions": ["notes:write"]`);
the user sees and approves them before enabling the plugin. Calling an ungranted
API throws an error.

### Sandbox & limits

Scripts run in a **Mozilla Rhino** interpreter that is locked down:

- **No Java / reflection / IO / network** — every Java class is denied. The only
  capability is the injected `octo` object.
- **No `Packages`, `getClass`, etc.** (sealed safe standard objects).
- **Instruction budget** — a runaway or infinite-loop script is aborted
  (~5,000,000 instructions). Keep commands fast and finite.
- A broken plugin is skipped, not fatal — but test it, because failures are silent
  to the user.

Write plain ES5-style JavaScript (`var`, `function`, regex, `String`/`Array`
methods). Full examples: [`text-tools`](text-tools/manifest.json) (pure transforms)
and [`note-actions`](note-actions/manifest.json) (uses `notes:write`).

> **Escaping tip:** author your JS in a normal `.js` file first, then JSON-escape
> it into `main`. In `bash`:
> `jq -Rs . my-plugin.js` prints the exact escaped string to paste.

---

## 6. Register it

Add an entry to [`plugins/registry.json`](registry.json). Keep the `manifestUrl`
pointing at `main`:

```json
{
  "id": "my-plugin",
  "name": "My Plugin",
  "author": "you",
  "description": "A short, punchy description.",
  "type": "theme",
  "version": "1.0.0",
  "manifestUrl": "https://raw.githubusercontent.com/l3ad3r1/Octo-Jotter/main/plugins/my-plugin/manifest.json"
}
```

For `script` plugins, also mirror the `permissions` array here so users see them in
the browser list before opening the plugin.

---

## 7. Test → submit checklist

1. **Validate JSON** — both files must parse:
   ```bash
   python -c "import json; json.load(open('plugins/my-plugin/manifest.json'))"
   python -c "import json; json.load(open('plugins/registry.json'))"
   ```
2. **`id` matches the folder name** and is unique in the registry.
3. **Theme:** every `onX` is readable on its `X`; check both a note list and the editor.
4. **Script:** commands are fast, finite, and only call APIs you listed in `permissions`.
5. **`minAppVersion`** is set to the lowest version that supports your features
   (theme `1.5`, script `1.6`, snippet `1.7`, `octo.notes.*` `1.8`).
6. **Open a PR** (or push to `main` if you own the repo). Once merged, it's live —
   pull to refresh in **Settings → Community Plugins**.

### Conventions

- `id` and snippet/command ids: lowercase `kebab-case`.
- `version`: bump it when you change a published plugin so the app can show updates.
- Keep `description` to one line; it's shown inline in the browser.
- Don't request permissions you don't use.

---

## 8. Troubleshooting

| Symptom | Likely cause |
|--------|--------------|
| Plugin doesn't appear in the browser | Registry JSON invalid, or not yet on `main`. Re-validate; pull to refresh. |
| "needs Octo Jotter X+" on install | `minAppVersion` is higher than the installed app. Lower it or update the app. |
| Theme installs but colors look wrong | Bad hex string, or an `onX`/`X` pair with poor contrast. |
| Script command missing after enable | JS threw at load (silently skipped). Check syntax; use `octo.log` and the debug log. |
| "Permission denied" when a command runs | You called `octo.notes.*` without listing it in `permissions`. |
