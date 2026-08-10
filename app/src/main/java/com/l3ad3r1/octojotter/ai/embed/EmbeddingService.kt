package com.l3ad3r1.octojotter.ai.embed

/**
 * Contract for embedding text into a fixed-dimensional float vector.
 *
 * Ported from Hermes Agent Android. The vectors returned MUST be L2-normalized so
 * that cosine similarity reduces to a dot product (see [FloatVectors.dot]).
 *
 * Phase 1 ships an ONNX all-MiniLM-L6-v2 implementation; a deterministic
 * placeholder ([PlaceholderEmbeddingService]) exists only to exercise the
 * indexing/search pipeline in tests and the debug screen — it is NOT semantically
 * meaningful and must never be the production embedder.
 */
interface EmbeddingService {

    /** Stable id/version of the embedder, persisted per row so an embedder swap
     *  can invalidate and re-index only stale vectors. */
    val modelId: String

    /** Dimensionality of the vectors produced by [embed]. */
    val dimension: Int

    /** Whether this embedder is ready to produce vectors (model loaded / available). */
    suspend fun isReady(): Boolean

    /**
     * Embed a single text into a fixed-length, L2-normalized float vector of size
     * [dimension].
     */
    suspend fun embed(text: String): FloatArray

    /** Batch variant — defaults to sequential embedding. */
    suspend fun embedAll(texts: List<String>): List<FloatArray> = texts.map { embed(it) }
}
