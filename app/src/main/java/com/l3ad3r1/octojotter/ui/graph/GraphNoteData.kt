package com.l3ad3r1.octojotter.ui.graph

/**
 * One note's id/title, the titles it [[wikilinks]] to, and its content, for Graph
 * View to lay out as a network and (when on-device AI is enabled) to describe a
 * relationship between two connected notes.
 *
 * [content] is blank and [locked] is true for a locked/encrypted note — the graph
 * shows its title as a node like any other, but its body must not leave the note
 * itself (same privacy invariant [NoteIndexer][com.l3ad3r1.octojotter.ai.index.NoteIndexer]
 * enforces for the embedding index).
 */
data class GraphNoteData(
    val id: Int,
    val title: String,
    val linkedTitles: List<String>,
    val content: String = "",
    val locked: Boolean = false,
)
