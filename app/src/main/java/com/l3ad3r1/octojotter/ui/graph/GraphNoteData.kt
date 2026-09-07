package com.l3ad3r1.octojotter.ui.graph

/** One note's id/title and the titles it [[wikilinks]] to, for Graph View to lay out as a network. */
data class GraphNoteData(
    val id: Int,
    val title: String,
    val linkedTitles: List<String>
)
