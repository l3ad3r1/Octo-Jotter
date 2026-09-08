package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.ui.graph.EdgeKind
import com.l3ad3r1.octojotter.ui.graph.GraphLayout
import com.l3ad3r1.octojotter.ui.graph.GraphNoteData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphLayoutTest {

    private fun note(id: Int, title: String, linkedTitles: List<String> = emptyList()) =
        GraphNoteData(id = id, title = title, linkedTitles = linkedTitles)

    @Test
    fun `wikilinks produce WIKILINK edges`() {
        val notes = listOf(note(1, "A", listOf("B")), note(2, "B"))

        val result = GraphLayout.compute(notes)

        assertEquals(1, result.edges.size)
        assertEquals(EdgeKind.WIKILINK, result.edges.single().kind)
    }

    @Test
    fun `semantic links are added for note pairs with no wikilink`() {
        val notes = listOf(note(1, "A"), note(2, "B"))

        val result = GraphLayout.compute(notes, semanticLinks = listOf(Triple(1, 2, 0.8f)))

        assertEquals(1, result.edges.size)
        val edge = result.edges.single()
        assertEquals(EdgeKind.SEMANTIC, edge.kind)
        assertEquals(0.8f, edge.weight, 1e-4f)
    }

    @Test
    fun `a semantic link is dropped when the pair is already wikilinked`() {
        val notes = listOf(note(1, "A", listOf("B")), note(2, "B"))

        val result = GraphLayout.compute(notes, semanticLinks = listOf(Triple(1, 2, 0.9f)))

        // Only the wikilink edge — no double edge between the same two nodes.
        assertEquals(1, result.edges.size)
        assertEquals(EdgeKind.WIKILINK, result.edges.single().kind)
    }

    @Test
    fun `degree counts both wikilink and semantic edges`() {
        val notes = listOf(note(1, "A", listOf("B")), note(2, "B"), note(3, "C"))

        val result = GraphLayout.compute(notes, semanticLinks = listOf(Triple(1, 3, 0.7f)))

        val nodeA = result.nodes.single { it.id == 1 }
        assertEquals(2, nodeA.degree)
    }

    @Test
    fun `a semantic link naming an unknown note id is ignored`() {
        val notes = listOf(note(1, "A"), note(2, "B"))

        val result = GraphLayout.compute(notes, semanticLinks = listOf(Triple(1, 999, 0.8f)))

        assertTrue(result.edges.isEmpty())
    }

    @Test
    fun `empty notes produce an empty layout regardless of semantic links`() {
        val result = GraphLayout.compute(emptyList(), semanticLinks = listOf(Triple(1, 2, 0.9f)))

        assertTrue(result.nodes.isEmpty())
        assertTrue(result.edges.isEmpty())
    }
}
