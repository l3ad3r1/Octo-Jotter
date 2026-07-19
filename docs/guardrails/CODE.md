# Editing code

- Read the target scope and imports before the first edit.
- Do not edit generated, vendored, build-output, or lock files directly.
- Search for duplicate definitions before changing a common name.
- Verify unfamiliar third-party API signatures from installed or official sources.
- Search all references after changing a public symbol, key, route, flag, or data shape.
- Preserve existing behavior unless the task explicitly changes it.
- Handle failures for new I/O, parsing, network, and multi-step mutation paths.
- Inspect the resulting diff and revert unrelated changes.
