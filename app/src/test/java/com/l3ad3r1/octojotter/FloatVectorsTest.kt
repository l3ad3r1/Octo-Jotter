package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.ai.embed.FloatVectors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class FloatVectorsTest {

    @Test
    fun `pack then unpack round-trips exactly`() {
        val v = floatArrayOf(0f, 1f, -1f, 3.14159f, 1e-7f, -2.5f)
        val back = FloatVectors.fromBytes(FloatVectors.toBytes(v))
        assertEquals(v.size, back.size)
        for (i in v.indices) assertEquals(v[i], back[i], 0f)
    }

    @Test
    fun `blob length is 4 bytes per dimension`() {
        val v = FloatArray(384) { it.toFloat() }
        assertEquals(384 * 4, FloatVectors.toBytes(v).size)
    }

    @Test
    fun `dot of identical unit vectors is 1`() {
        val v = FloatVectors.normalize(floatArrayOf(3f, 4f)) // -> (0.6, 0.8)
        assertEquals(1f, FloatVectors.dot(v, v), 1e-6f)
    }

    @Test
    fun `dot of orthogonal unit vectors is 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, FloatVectors.dot(a, b), 1e-6f)
    }

    @Test
    fun `normalize yields unit length`() {
        val n = FloatVectors.normalize(floatArrayOf(3f, 4f, 12f))
        var len = 0f
        for (x in n) len += x * x
        assertTrue(abs(sqrt(len) - 1f) < 1e-6f)
    }

    @Test
    fun `normalize of zero vector does not divide by zero`() {
        val n = FloatVectors.normalize(floatArrayOf(0f, 0f, 0f))
        for (x in n) assertEquals(0f, x, 0f)
    }
}
