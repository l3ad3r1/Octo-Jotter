package com.l3ad3r1.octojotter.ai.index

import com.l3ad3r1.octojotter.ai.embed.FloatVectors
import com.l3ad3r1.octojotter.data.local.NoteEmbeddingDao
import com.l3ad3r1.octojotter.data.local.NoteEmbeddingEntity

/** Two notes whose content is semantically similar, for Graph View's semantic-edge
 *  overlay — inspired by Kwipu's vector-similarity retrieval, but computed purely
 *  from the on-device embedding index already built for search/RAG. */
data class SimilarNotePair(val noteIdA: Int, val noteIdB: Int, val score: Float)

/**
 * Note-level semantic similarity, reusing the chunk vectors [NoteIndexer] already
 * built for search/RAG (spec §6.2). A note's vector is the average of its chunk
 * vectors, re-normalized; pairs are ranked by cosine similarity and capped per
 * note so one generic note doesn't connect to everything. Brute-force O(n^2) over
 * notes — same reasoning as [VectorStore], fine at this app's scale.
 *
 * Locked/encrypted notes are never embedded (privacy invariant P2, see
 * [NoteIndexer]), so they never appear here either — nothing extra to filter.
 */
class NoteSimilarity(private val dao: NoteEmbeddingDao) {
    suspend fun topPairs(
        modelId: String,
        threshold: Float = DEFAULT_THRESHOLD,
        maxPerNote: Int = DEFAULT_MAX_PER_NOTE,
    ): List<SimilarNotePair> = computeSimilarPairs(dao.allActive().filter { it.model == modelId }, threshold, maxPerNote)

    companion object {
        const val DEFAULT_THRESHOLD = 0.55f
        const val DEFAULT_MAX_PER_NOTE = 3
    }
}

/** Pure core of [NoteSimilarity.topPairs], testable without a DAO. */
internal fun computeSimilarPairs(
    rows: List<NoteEmbeddingEntity>,
    threshold: Float,
    maxPerNote: Int,
): List<SimilarNotePair> {
    val vectorByNote = rows.groupBy { it.noteId }
        .mapValues { (_, chunks) -> averageNormalized(chunks.map { FloatVectors.fromBytes(it.vector) }) }
    val ids = vectorByNote.keys.toList()

    val candidates = mutableListOf<SimilarNotePair>()
    for (i in ids.indices) {
        for (j in i + 1 until ids.size) {
            val a = ids[i]
            val b = ids[j]
            val score = FloatVectors.dot(vectorByNote.getValue(a), vectorByNote.getValue(b))
            if (score >= threshold) candidates += SimilarNotePair(a, b, score)
        }
    }

    // Greedy strongest-first, skipping a pair once either endpoint has already
    // reached maxPerNote. Ranking each note's own candidates independently and
    // taking its top-K does NOT bound its final degree — a note can still be
    // pulled in by other notes' independent picks — so the cap has to be
    // enforced on the merged, sorted list instead.
    val degree = HashMap<Int, Int>()
    val kept = mutableListOf<SimilarNotePair>()
    for (pair in candidates.sortedByDescending { it.score }) {
        val degreeA = degree.getOrDefault(pair.noteIdA, 0)
        val degreeB = degree.getOrDefault(pair.noteIdB, 0)
        if (degreeA < maxPerNote && degreeB < maxPerNote) {
            kept += pair
            degree[pair.noteIdA] = degreeA + 1
            degree[pair.noteIdB] = degreeB + 1
        }
    }
    return kept.sortedByDescending { it.score }
}

private fun averageNormalized(vectors: List<FloatArray>): FloatArray {
    val dim = vectors.first().size
    val sum = FloatArray(dim)
    for (v in vectors) for (d in 0 until dim) sum[d] += v[d]
    return FloatVectors.normalize(sum)
}
