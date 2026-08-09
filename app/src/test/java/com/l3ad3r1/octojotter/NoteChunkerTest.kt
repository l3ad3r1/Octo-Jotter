package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.ai.index.NoteChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteChunkerTest {

    @Test
    fun `empty or blank content yields no chunks`() {
        val c = NoteChunker()
        assertTrue(c.chunk("").isEmpty())
        assertTrue(c.chunk("   \n\n  \t ").isEmpty())
    }

    @Test
    fun `short note is a single chunk`() {
        val chunks = NoteChunker().chunk("# Title\n\nA short paragraph about octopuses.")
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("octopuses"))
    }

    @Test
    fun `paragraphs are packed and split under the char budget`() {
        val para = "word ".repeat(60).trim() // ~300 chars
        val content = (1..10).joinToString("\n\n") { "Para $it: $para" }
        val chunks = NoteChunker(maxChars = 400, overlapChars = 40).chunk(content)
        assertTrue("expected multiple chunks, got ${chunks.size}", chunks.size > 1)
        // No chunk grossly exceeds the budget (allow overlap + one block slack).
        assertTrue(chunks.all { it.length <= 400 + 300 })
    }

    @Test
    fun `a single oversized block is hard-split`() {
        val huge = "lorem ".repeat(500).trim() // ~3000 chars, one block
        val chunks = NoteChunker(maxChars = 500, overlapChars = 0).chunk(huge)
        assertTrue(chunks.size >= 5)
        assertTrue(chunks.all { it.length <= 500 })
    }

    @Test
    fun `overlap carries trailing context into the next chunk`() {
        val a = "alpha ".repeat(50).trim()
        val b = "bravo ".repeat(50).trim()
        val chunks = NoteChunker(maxChars = 320, overlapChars = 60).chunk("$a\n\n$b")
        assertTrue(chunks.size >= 2)
        // The second chunk should begin with some overlap from the first.
        assertTrue(chunks[1].contains("alpha"))
    }
}
