package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.ai.embed.FloatVectors
import com.l3ad3r1.octojotter.ai.index.computeSimilarPairs
import com.l3ad3r1.octojotter.data.local.NoteEmbeddingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSimilarityTest {

    private fun row(noteId: Int, chunkIndex: Int, vector: FloatArray) = NoteEmbeddingEntity(
        noteId = noteId,
        chunkIndex = chunkIndex,
        chunkText = "chunk $noteId.$chunkIndex",
        vector = FloatVectors.toBytes(vector),
        contentHash = "hash-$noteId-$chunkIndex",
        model = "test-model",
    )

    @Test
    fun `identical notes score 1 and unrelated notes are excluded`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        val rows = listOf(row(1, 0, a), row(2, 0, a), row(3, 0, b))

        val pairs = computeSimilarPairs(rows, threshold = 0.5f, maxPerNote = 10)

        assertEquals(1, pairs.size)
        val pair = pairs.single()
        assertEquals(1, pair.noteIdA)
        assertEquals(2, pair.noteIdB)
        assertEquals(1f, pair.score, 1e-4f)
    }

    @Test
    fun `below-threshold pairs are dropped`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        val rows = listOf(row(1, 0, a), row(2, 0, b))

        val pairs = computeSimilarPairs(rows, threshold = 0.9f, maxPerNote = 10)

        assertTrue(pairs.isEmpty())
    }

    @Test
    fun `a note's chunks are averaged into one vector before comparing`() {
        // Note 1's two chunks average to (1, 0) after re-normalizing, matching note 2 exactly.
        val rows = listOf(
            row(1, 0, floatArrayOf(1f, 1f)),
            row(1, 1, floatArrayOf(1f, -1f)),
            row(2, 0, floatArrayOf(1f, 0f)),
        )

        val pairs = computeSimilarPairs(rows, threshold = 0.9f, maxPerNote = 10)

        assertEquals(1, pairs.size)
        assertEquals(1f, pairs.single().score, 1e-3f)
    }

    @Test
    fun `fan-out per note is capped to the strongest matches`() {
        val base = floatArrayOf(1f, 0f)
        // Four other notes all near-identical to note 1 and to each other.
        val rows = (1..5).map { row(it, 0, base) }

        val pairs = computeSimilarPairs(rows, threshold = 0.5f, maxPerNote = 2)

        val degree = HashMap<Int, Int>()
        pairs.forEach { degree[it.noteIdA] = (degree[it.noteIdA] ?: 0) + 1; degree[it.noteIdB] = (degree[it.noteIdB] ?: 0) + 1 }
        assertTrue("expected every note capped at 2 kept pairs, got $degree", degree.values.all { it <= 2 })
    }

    @Test
    fun `no rows means no pairs`() {
        assertTrue(computeSimilarPairs(emptyList(), threshold = 0.5f, maxPerNote = 3).isEmpty())
    }
}
