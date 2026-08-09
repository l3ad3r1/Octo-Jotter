package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.ai.embed.WordPieceTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordPieceTokenizerTest {

    // id == index; mirrors a HuggingFace vocab.txt.
    private val vocab = listOf(
        "[PAD]", "[UNK]", "[CLS]", "[SEP]",
        "play", "##ing", "cafe", "!", "octopus", "the",
    ).withIndex().associate { (i, tok) -> tok to i }

    private fun tok(maxTokens: Int = 256) = WordPieceTokenizer(vocab, maxTokens = maxTokens)

    @Test
    fun `wraps with CLS and SEP`() {
        val e = tok().encode("octopus")
        assertEquals(listOf(2L, 8L, 3L), e.ids.toList())
        assertEquals(e.ids.size, e.attentionMask.size)
        assertTrue(e.attentionMask.all { it == 1L })
    }

    @Test
    fun `greedy wordpiece splits known continuations`() {
        val e = tok().encode("playing")
        assertEquals(listOf(2L, 4L, 5L, 3L), e.ids.toList()) // [CLS] play ##ing [SEP]
    }

    @Test
    fun `lowercases and strips accents`() {
        val e = tok().encode("Café!")
        assertEquals(listOf(2L, 6L, 7L, 3L), e.ids.toList()) // [CLS] cafe ! [SEP]
    }

    @Test
    fun `unknown word maps to UNK`() {
        val e = tok().encode("zzzz")
        assertEquals(listOf(2L, 1L, 3L), e.ids.toList())
    }

    @Test
    fun `punctuation is split from words`() {
        val e = tok().encode("the octopus!")
        assertEquals(listOf(2L, 9L, 8L, 7L, 3L), e.ids.toList()) // the octopus ! wrapped
    }

    @Test
    fun `truncates to maxTokens including CLS and SEP`() {
        val e = tok(maxTokens = 4).encode("the octopus the octopus the")
        assertEquals(4, e.ids.size)
        assertEquals(2L, e.ids.first())
        assertEquals(3L, e.ids.last())
    }
}
