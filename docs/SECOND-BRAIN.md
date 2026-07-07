# Octo Jotter as a Second Brain

A **Second Brain** (from Tiago Forte's *Building a Second Brain*) is a trusted
external system for your ideas, tasks, projects, and learnings. Obsidian is a
popular home for one; Octo Jotter gives you a lightweight, **offline-first,
Git-backed** vault in your pocket — your notes live in a **GitHub repository** of
plain Markdown you fully own, versioned and portable.

This guide maps a classic Obsidian second-brain setup onto Octo Jotter and the
community plugins that reproduce the core workflow.

---

## 1. Your vault = a GitHub repo

Octo Jotter can sync an entire GitHub **repository** of Markdown (not just Gists).
Point it at a repo like `you/second-brain` and every `.md` file becomes a note,
with the repo's folders preserved.

**Setup**
1. Create a private repo, e.g. `second-brain`.
2. In Octo Jotter: **Settings → GitHub** — paste a Personal Access Token with the
   **`repo`** scope (private repos need it).
3. **Settings → Repositories** — add `you/second-brain` and pull.
4. Notes now nest by folder in the drawer and grouped list, exactly like a vault.

> Push/pull for repositories is **manual** (tap sync) so you stay in control of
> commits. Edits you make on-device push back as commits to the repo.

---

## 2. The vault structure

A proven PARA-style layout — create these folders in your repo (or let the
templates seed them):

| Folder | Purpose |
|--------|---------|
| `00 Inbox` | Quick capture; triage later. |
| `01 Daily Notes` | One note per day — log, tasks, reflections. |
| `02 Projects` | A folder per project: notes, todos, meetings, references. |
| `03 Code Snippets` | Reusable functions, commands, one-liners. |
| `04 Architecture & Design` | System decisions, diagrams, patterns (ADRs). |
| `05 Learnings` | Takeaways from books, courses, articles. |
| `Files` | PDFs, images, attachments referenced by notes. |
| `Templates` | Reference copies of your note formats. |

Octo Jotter also supports **`[[wiki-links]]`** between notes and **`#tags`**, so
your vault stays interlinked and filterable just like in Obsidian.

---

## 3. Reproduce the Obsidian plugin workflow

Install these from **Settings → Community Plugins** (pull to refresh):

| Obsidian plugin | Octo Jotter equivalent | Status |
|-----------------|------------------------|--------|
| **Templater** (structured notes) | **Second Brain Templates** (snippets) + **Second Brain Tools** (dated daily note) | ✅ Available |
| **Tasks** (todo management) | **Second Brain Tools** (extract open tasks) + **Sort Tasks** + native `- [ ]`/`- [x]` rendering | ✅ Available |
| **Table of contents** | **Table of Contents** script | ✅ Available |
| **Dataview** (query notes) | Script API can list note titles today; richer metadata queries are on the roadmap | 🟡 Partial |
| **Kanban** (boards) | Board view is an app feature on the roadmap | 🔜 Roadmap |
| **Excalidraw** (drawings) | Freeform canvas is an app feature on the roadmap | 🔜 Roadmap |

### Templates (Templater)
**Second Brain Templates** adds insertable templates for: daily note, weekly
review, project, meeting notes, retrospective, bug report, code snippet, learning
/ book notes, ADR, and inbox capture. Insert one from the editor's snippet menu.

**Second Brain Tools** adds a **New daily note (dated)** command that stamps
today's date and scaffolds Focus / Tasks / Log / Captures sections.

### Tasks
- Write tasks as `- [ ]` / `- [x]` — they render as checkboxes in preview.
- **Extract open tasks to top** (Second Brain Tools) surfaces everything still
  open at the top of a note — a lightweight "task dashboard" per note.
- **Sort Tasks** moves completed items to the bottom.

### Capture → Organize → Distill → Express (CODE)
- **Capture:** the *Inbox Capture* template (or the `00 Inbox` folder).
- **Organize:** move notes into `01`–`05` folders; tag with `#project`, `#learning`.
- **Distill:** use headings + **Table of Contents**; highlight key lines.
- **Express:** link notes with `[[wiki-links]]` to build your own web of ideas.

---

## 4. Recommended starter setup

1. Sync your `second-brain` repo (§1).
2. Install & enable: **Second Brain Templates**, **Second Brain Tools**,
   **Table of Contents**, **Sort Tasks**.
3. Pick a theme — **Midnight** / **Emerald** for focus, **Daybreak** for daytime.
4. Each morning: new note → **New daily note (dated)** → work the day → **Sort
   Tasks** to tidy.
5. Weekly: insert the **Weekly Review** template and reflect.

---

## 5. Roadmap (app features, not plugins)

These need in-app support beyond the current plugin API and are tracked for future
releases:

- **Kanban board** view over a project's tasks.
- **Excalidraw-style canvas** for diagrams embedded in notes.
- **Dataview-style queries** — a richer script API exposing note content,
  front-matter, and tags so plugins can build live dashboards.

Want to help? See [`CONTRIBUTING.md`](../CONTRIBUTING.md) and the
[plugin authoring guide](../plugins/README.md).
