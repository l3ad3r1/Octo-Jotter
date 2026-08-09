package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.ai.chat.RagPrompt
import com.l3ad3r1.octojotter.ai.index.ChunkHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RagPromptTest {

    private fun hit(noteId: Int, chunk: Int, text: String, score: Float) =
        ChunkHit(noteId = noteId, chunkIndex = chunk, chunkText = text, score = score)

    @Test
    fun `no chunks yields a no-context prompt and no citations`() {
        val ctx = RagPrompt.build("anything", emptyList())
        assertFalse(ctx.hasGrounding)
        assertTrue(ctx.citations.isEmpty())
        assertTrue(ctx.system.contains("couldn't find", ignoreCase = true) ||
            ctx.system.contains("No relevant notes", ignoreCase = true))
    }

    @Test
    fun `chunks become cited notes in the system prompt`() {
        val ctx = RagPrompt.build(
            "what is an octopus",
            listOf(
                hit(3, 0, "The octopus is a cephalopod.", 0.9f),
                hit(7, 0, "Rent is due monthly.", 0.2f),
            ),
        )
        assertTrue(ctx.hasGrounding)
        assertTrue(ctx.system.contains("[Note 3]"))
        assertTrue(ctx.system.contains("[Note 7]"))
        assertTrue(ctx.system.contains("cephalopod"))
        assertEquals(listOf(3, 7), ctx.citations.map { it.noteId })
        assertEquals("what is an octopus", ctx.question)
    }

    @Test
    fun `multiple chunks from one note collapse to a single best citation`() {
        val ctx = RagPrompt.build(
            "octopus",
            listOf(
                hit(3, 0, "weaker chunk", 0.3f),
                hit(3, 1, "stronger chunk about octopus", 0.8f),
            ),
        )
        assertEquals(1, ctx.citations.size)
        assertEquals(3, ctx.citations[0].noteId)
        assertTrue(ctx.system.contains("stronger chunk"))
        assertFalse(ctx.system.contains("weaker chunk"))
    }

    @Test
    fun `context is bounded by the char budget`() {
        val big = "x".repeat(500)
        val chunks = (1..20).map { hit(it, 0, big, 1f / it) }
        val ctx = RagPrompt.build("q", chunks, maxChars = 1200)
        // Only a couple of ~500-char notes fit under the budget.
        assertTrue(ctx.citations.size < 20)
        assertTrue(ctx.citations.isNotEmpty())
    }
}
