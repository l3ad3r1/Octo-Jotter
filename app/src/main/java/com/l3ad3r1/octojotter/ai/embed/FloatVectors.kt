package com.l3ad3r1.octojotter.ai.embed

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Packing and math helpers for embedding vectors.
 *
 * Vectors are stored in Room as a little-endian float BLOB (4 bytes per dim).
 * Because embedders return L2-normalized vectors, cosine similarity between two
 * stored vectors reduces to a plain dot product — see [dot].
 */
object FloatVectors {

    /** Pack a float vector into a little-endian byte array (4 bytes per element). */
    fun toBytes(vector: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        for (v in vector) buf.putFloat(v)
        return buf.array()
    }

    /** Unpack a little-endian float BLOB back into a float vector. */
    fun fromBytes(bytes: ByteArray): FloatArray {
        require(bytes.size % Float.SIZE_BYTES == 0) {
            "Vector blob length ${bytes.size} is not a multiple of ${Float.SIZE_BYTES}"
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(bytes.size / Float.SIZE_BYTES)
        for (i in out.indices) out[i] = buf.getFloat()
        return out
    }

    /** Dot product. For L2-normalized inputs this equals cosine similarity. */
    fun dot(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vector size mismatch: ${a.size} vs ${b.size}" }
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    /**
     * Return an L2-normalized copy of [vector]. A zero (or near-zero) vector is
     * returned unchanged to avoid division by zero.
     */
    fun normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = sqrt(norm)
        if (norm < 1e-12f) return vector.copyOf()
        val out = FloatArray(vector.size)
        for (i in vector.indices) out[i] = vector[i] / norm
        return out
    }
}
