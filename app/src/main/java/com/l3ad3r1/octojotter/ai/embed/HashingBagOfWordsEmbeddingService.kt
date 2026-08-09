package com.l3ad3r1.octojotter.ai.embed

/**
 * Deterministic, model-free embedder for pipeline testing and as a last-resort
 * fallback — NOT the production embedder.
 *
 * Strategy: hash each lowercased word into [dimension] buckets and accumulate,
 * then L2-normalize. Unlike a pure text hash, this reflects *lexical* overlap —
 * two texts that share words get non-orthogonal vectors — which is enough to
 * exercise indexing and ranking end-to-end and to write meaningful search tests.
 * It carries no real semantic understanding, so [OnnxMiniLmEmbeddingService] must
 * replace it whenever the model is available.
 */
class HashingBagOfWordsEmbeddingService(
    override val dimension: Int = 384,
) : EmbeddingService {

    override val modelId: String = "hashing-bow-v1"

    override suspend fun isReady(): Boolean = true

    override suspend fun embed(text: String): FloatArray {
        val vec = FloatArray(dimension)
        val words = text.lowercase().split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.isNotEmpty() }
        for (w in words) {
            val h = w.hashCode()
            val bucket = ((h % dimension) + dimension) % dimension
            val sign = if ((h ushr 31) and 1 == 0) 1f else -1f
            vec[bucket] += sign
        }
        return FloatVectors.normalize(vec)
    }
}
