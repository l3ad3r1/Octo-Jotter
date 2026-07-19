# Guardrail format

Keep instructions short, concrete, and event-driven.

- Put shared behavior in the nearest parent `CLAUDE.md`; project files contain only local facts.
- Give each rule one action and one reason.
- Prefer commands and measurable thresholds to judgment words.
- Keep each guardrail document under 30 lines and avoid duplicated rules.
- Preserve project commands, safety constraints, and architecture facts during rewrites.
