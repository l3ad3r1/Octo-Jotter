package com.l3ad3r1.octojotter.ui.graph

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

data class GraphNode(val id: Int, val title: String, var x: Float, var y: Float, val degree: Int)
data class GraphEdge(val fromIndex: Int, val toIndex: Int)
data class GraphLayoutResult(val nodes: List<GraphNode>, val edges: List<GraphEdge>)

/**
 * A small, dependency-free Fruchterman-Reingold force-directed layout: nodes
 * repel each other, edges pull their endpoints together, repeated for a fixed
 * number of iterations. Good enough for the note counts this app deals with
 * (hundreds, not tens of thousands) — no need for a real graph-layout library
 * for a single static-then-draggable view.
 */
object GraphLayout {
    fun compute(
        notes: List<GraphNoteData>,
        width: Float = 1000f,
        height: Float = 1000f,
        iterations: Int = 300
    ): GraphLayoutResult {
        if (notes.isEmpty()) return GraphLayoutResult(emptyList(), emptyList())

        val indexByTitle = notes.withIndex().associate { (i, n) -> n.title to i }
        val edges = mutableListOf<GraphEdge>()
        notes.forEachIndexed { i, note ->
            note.linkedTitles.forEach { linked ->
                indexByTitle[linked]?.let { j -> if (j != i) edges += GraphEdge(i, j) }
            }
        }
        val degree = IntArray(notes.size)
        edges.forEach { degree[it.fromIndex]++; degree[it.toIndex]++ }

        val random = Random(42) // deterministic layout across recompositions
        val n = notes.size
        val x = FloatArray(n) { random.nextFloat() * width }
        val y = FloatArray(n) { random.nextFloat() * height }

        val area = width * height
        val k = sqrt(area / max(n, 1))
        var temperature = width / 10f

        repeat(iterations) {
            val dispX = FloatArray(n)
            val dispY = FloatArray(n)

            // Repulsion between every pair — O(n^2), fine for a note graph's scale.
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    var dx = x[i] - x[j]
                    var dy = y[i] - y[j]
                    var dist = sqrt(dx * dx + dy * dy)
                    if (dist < 0.01f) { dx = random.nextFloat() - 0.5f; dy = random.nextFloat() - 0.5f; dist = 0.01f }
                    val force = (k * k) / dist
                    val fx = dx / dist * force
                    val fy = dy / dist * force
                    dispX[i] += fx; dispY[i] += fy
                    dispX[j] -= fx; dispY[j] -= fy
                }
            }

            // Attraction along edges.
            edges.forEach { e ->
                val i = e.fromIndex; val j = e.toIndex
                var dx = x[i] - x[j]
                var dy = y[i] - y[j]
                var dist = sqrt(dx * dx + dy * dy)
                if (dist < 0.01f) dist = 0.01f
                val force = (dist * dist) / k
                val fx = dx / dist * force
                val fy = dy / dist * force
                dispX[i] -= fx; dispY[i] -= fy
                dispX[j] += fx; dispY[j] += fy
            }

            for (i in 0 until n) {
                val d = sqrt(dispX[i] * dispX[i] + dispY[i] * dispY[i]).coerceAtLeast(0.01f)
                x[i] = (x[i] + (dispX[i] / d) * min(d, temperature)).coerceIn(0f, width)
                y[i] = (y[i] + (dispY[i] / d) * min(d, temperature)).coerceIn(0f, height)
            }
            temperature *= 0.97f
        }

        val nodes = notes.mapIndexed { i, note ->
            GraphNode(id = note.id, title = note.title, x = x[i], y = y[i], degree = degree[i])
        }
        return GraphLayoutResult(nodes, edges)
    }
}
