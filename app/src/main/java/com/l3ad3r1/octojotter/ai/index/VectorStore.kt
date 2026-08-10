package com.l3ad3r1.octojotter.ai.index

import com.l3ad3r1.octojotter.ai.embed.FloatVectors
import com.l3ad3r1.octojotter.data.local.NoteEmbeddingDao
import com.l3ad3r1.octojotter.data.local.NoteEmbeddingEntity

/** One retrieved chunk with its similarity to the query. */
data class ChunkHit(
    val noteId: Int,
    val chunkIndex: Int,
    val chunkText: String,
    val score: Float,
)

/** A note ranked by its best-matching chunk. */
data class NoteHit(
    val noteId: Int,
    val score: Float,
    val bestChunk: String,
)

/**
 * Room-backed vector search (spec §6.2). Brute-force cosine over all active
 * chunk vectors — L2-normalized vectors mean cosine == dot product. This is a
 * few ms for realistic vaults (thousands of notes → tens of thousands of
 * chunks); revisit an ANN index only past ~50k chunks.
 */
class VectorStore(private val dao: NoteEmbeddingDao) {

    /**
     * Top-[k] chunks most similar to [queryVector], highest score first.
     * [modelId], when set, restricts the comparison to vectors produced by that
     * embedder — vectors from a different model live in a different space and a
     * dot product across them is meaningless even at equal dimensionality.
     */
    suspend fun topKChunks(queryVector: FloatArray, k: Int = 6, modelId: String? = null): List<ChunkHit> {
        require(k > 0) { "k must be > 0" }
        return rankChunks(queryVector, dao.allActive(), k, modelId)
    }

    /** Top-[k] notes, each scored by its single best-matching chunk. */
    suspend fun topKNotes(queryVector: FloatArray, k: Int = 10, modelId: String? = null): List<NoteHit> {
        val best = HashMap<Int, ChunkHit>()
        for (hit in rankChunks(queryVector, dao.allActive(), limit = Int.MAX_VALUE, modelId = modelId)) {
            val existing = best[hit.noteId]
            if (existing == null || hit.score > existing.score) best[hit.noteId] = hit
        }
        return best.values
            .sortedByDescending { it.score }
            .take(k)
            .map { NoteHit(it.noteId, it.score, it.chunkText) }
    }

    private fun rankChunks(
        queryVector: FloatArray,
        rows: List<NoteEmbeddingEntity>,
        limit: Int,
        modelId: String? = null,
    ): List<ChunkHit> =
        rows.asSequence()
            .filter { modelId == null || it.model == modelId }
            .map { row ->
                val v = FloatVectors.fromBytes(row.vector)
                val score = if (v.size == queryVector.size) FloatVectors.dot(queryVector, v) else Float.NEGATIVE_INFINITY
                ChunkHit(row.noteId, row.chunkIndex, row.chunkText, score)
            }
            .filter { it.score > Float.NEGATIVE_INFINITY }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
}
