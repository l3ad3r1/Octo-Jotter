# Plugin starter template

Copy one of these to start a new plugin. **Do not edit files in `_template/`
directly** — this folder is a reference and is intentionally left out of
[`registry.json`](../registry.json), so nothing here is installable.

## Steps

1. Pick the starter that matches your plugin type:
   - `theme.manifest.json` — re-colors the app
   - `snippet.manifest.json` — insertable Markdown templates
   - `script.manifest.json` — JavaScript editor commands
2. Create a new folder named after your plugin id, e.g. `plugins/my-plugin/`.
3. Copy the chosen starter there and rename it to `manifest.json`:
   ```bash
   mkdir plugins/my-plugin
   cp plugins/_template/theme.manifest.json plugins/my-plugin/manifest.json
   ```
4. Fill in `id` (must match the folder name), `name`, `author`, `description`, and
   your content. Bump `minAppVersion` only if needed (theme `1.5`, script `1.6`,
   snippet `1.7`, `octo.notes.*` `1.8`).
5. Add a matching entry to [`registry.json`](../registry.json) with a `manifestUrl`
   pointing at `.../plugins/my-plugin/manifest.json` on `main`.
6. Validate and submit — see the full guide in [`../README.md`](../README.md).

## Validate before committing

```bash
python -c "import json; json.load(open('plugins/my-plugin/manifest.json'))"
python -c "import json; json.load(open('plugins/registry.json'))"
```
