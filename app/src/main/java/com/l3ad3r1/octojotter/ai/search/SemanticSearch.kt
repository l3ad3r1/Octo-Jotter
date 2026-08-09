package com.l3ad3r1.octojotter.ai.search

import com.l3ad3r1.octojotter.ai.embed.EmbeddingService
import com.l3ad3r1.octojotter.ai.index.VectorStore

/** A ranked note result from hybrid search. */
data class SearchResult(
    val noteId: Int,
    val score: Float,
    val bestChunk: String?,
    val keywordMatch: Boolean,
    val semanticScore: Float,
)

/**
 * Hybrid semantic + keyword search (spec §6.2).
 *
 * Final score = α·semantic + (1−α)·keyword, where semantic is the (clamped)
 * cosine of the best-matching chunk and keyword is 1 if the note matched the
 * existing substring search else 0. Blending keeps exact-term matches (names,
 * ids, code) that embeddings alone miss while surfacing meaning-based matches.
 *
 * If the embedder isn't ready, this degrades to keyword-only so callers always
 * get results.
 */
class SemanticSearch(
    private val embedder: EmbeddingService,
    private val vectorStore: VectorStore,
    private val keyword: KeywordSource,
    private val alpha: Float = 0.7f,
) {
    /** Note ids that match the query by substring (the existing LIKE path). */
    interface KeywordSource {
        suspend fun matchingNoteIds(query: String): Set<Int>
    }

    suspend fun search(query: String, k: Int = 20): List<SearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val keywordIds = keyword.matchingNoteIds(q)

        if (!embedder.isReady()) {
            return keywordIds.map {
                SearchResult(it, score = 1f - alpha, bestChunk = null, keywordMatch = true, semanticScore = 0f)
            }.take(k)
        }

        val qVec = embedder.embed(q)
        val semantic = vectorStore.topKNotes(qVec, k = k * 3, modelId = embedder.modelId)
            .associate { it.noteId to it }

        val ids = keywordIds + semantic.keys
        return ids.map { id ->
            val sem = semantic[id]
            val semScore = sem?.score?.coerceIn(0f, 1f) ?: 0f
            val kw = if (id in keywordIds) 1f else 0f
            SearchResult(
                noteId = id,
                score = alpha * semScore + (1f - alpha) * kw,
                bestChunk = sem?.bestChunk,
                keywordMatch = id in keywordIds,
                semanticScore = semScore,
            )
        }.sortedByDescending { it.score }.take(k)
    }
}
