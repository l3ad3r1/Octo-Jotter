# Octo Jotter as a Second Brain

A Second Brain is a trusted external system for your ideas, tasks, projects,
and learnings. Octo Jotter gives you a lightweight, offline-first, Git-backed
vault in your pocket: plain Markdown notes you own, sync, version, and extend.

This guide maps a classic Obsidian second-brain setup onto Octo Jotter and the
community plugins that reproduce the core workflow.

---

## 1. Your vault = a GitHub repo

Octo Jotter can sync an entire GitHub repository of Markdown, not just Gists.
Point it at a repo like `you/second-brain` and every `.md` file becomes a note,
with repo folders preserved.

**Setup**
1. Create a private repo, e.g. `second-brain`.
2. In Octo Jotter: **Settings -> GitHub** and paste a Personal Access Token with
   the `repo` scope.
3. **Settings -> Repositories** and add `you/second-brain`.
4. Tap sync. Notes now nest by folder in the drawer and grouped list.

Repository sync is manual, so you stay in control of pushes and pulls.

---

## 2. The vault structure

A proven PARA-style layout:

| Folder | Purpose |
|--------|---------|
| `00 Inbox` | Quick capture; triage later. |
| `01 Daily Notes` | One note per day: log, tasks, reflections. |
| `02 Projects` | A folder per project: notes, todos, meetings, references. |
| `03 Code Snippets` | Reusable functions, commands, one-liners. |
| `04 Architecture & Design` | System decisions, diagrams, patterns, ADRs. |
| `05 Learnings` | Takeaways from books, courses, articles. |
| `Files` | PDFs, images, attachments referenced by notes. |
| `Templates` | Reference copies of your note formats. |

Octo Jotter also supports `[[wiki-links]]` and `#tags`, so your vault stays
interlinked and filterable.

---

## 3. Obsidian workflow equivalents

Install these from **Settings -> Community Plugins**.

| Obsidian plugin | Octo Jotter equivalent | Status |
|-----------------|------------------------|--------|
| Templater | Second Brain Templates + Second Brain Tools | Available |
| Tasks | Task Board, Open Tasks Dashboard, Second Brain Tools, Sort Tasks, native checkboxes | Available |
| Table of contents | Table of Contents script | Available |
| Dataview | `notes:read` script API: note bodies, tags, folders, timestamps, search, tag filters, open tasks | Available in v2.2+ |
| Kanban | Task Board groups open/done Markdown tasks across notes | Available in v2.3+ |
| Excalidraw | Freeform canvas is an app feature on the roadmap | Roadmap |

### Templates

Second Brain Templates adds daily note, weekly review, project, meeting notes,
retrospective, bug report, code snippet, learning/book notes, ADR, and inbox
capture templates.

Second Brain Tools adds a **New daily note (dated)** command that stamps today's
date and scaffolds Focus, Tasks, Log, and Captures sections.

### Tasks

- Write tasks as `- [ ]` / `- [x]`; they render as checkboxes in preview.
- Task Board gives you an in-app board over open and completed tasks across
  unlocked notes, with one-tap checkbox updates back to the source note.
- Open Tasks Dashboard scans readable notes and generates a cross-note task
  dashboard with source note links and line numbers.
- Extract open tasks to top surfaces everything still open inside one note.
- Sort Tasks moves completed items to the bottom.

### Dataview-style queries

Script plugins can request `notes:read` and call:

- `octo.notes.list()` for read-only note snapshots.
- `octo.notes.titles()` for a compatibility array of note titles.
- `octo.notes.search(query)` for title/body/tag/folder search.
- `octo.notes.withTag(tag)` for tag-filtered notes.
- `octo.notes.openTasks()` for open Markdown tasks across notes.

Locked notes appear in query results as locked metadata, but their body content
is redacted from plugins.

### Capture -> Organize -> Distill -> Express

- Capture: the Inbox Capture template or the `00 Inbox` folder.
- Organize: move notes into `01`-`05` folders; tag with `#project`, `#learning`.
- Distill: use headings, Table of Contents, and short summaries.
- Express: link notes with `[[wiki-links]]` to build your own web of ideas.

---

## 4. Recommended starter setup

1. Sync your `second-brain` repo.
2. Install and enable Second Brain Templates, Second Brain Tools, Table of
   Contents, Sort Tasks, and Open Tasks Dashboard.
3. Pick a theme: Midnight or Emerald for focus, Daybreak for daytime.
4. Each morning: create a daily note, work the day, then sort tasks.
5. Open Task Board from the drawer to review and close loops.
6. Weekly: insert the Weekly Review template and reflect.

---

## 5. Roadmap

These need app-level support beyond the current plugin API:

- Excalidraw-style canvas for diagrams embedded in notes.
- Saved query dashboards built from the v2.2 read-only script API.

See the [plugin authoring guide](../plugins/README.md) to build more
second-brain plugins.
