package com.l3ad3r1.octojotter.ui.graph

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

data class GraphNode(val id: Int, val title: String, var x: Float, var y: Float, val degree: Int)

/** [WIKILINK] edges come from an explicit `[[link]]`; [SEMANTIC] edges are a
 *  same-meaning note pair found by embedding similarity with no such link — see
 *  [com.l3ad3r1.octojotter.ai.index.NoteSimilarity]. Kept out of the layout when
 *  a wikilink already connects the same pair, so a link is never drawn twice. */
enum class EdgeKind { WIKILINK, SEMANTIC }

/** [weight] is the cosine similarity for a [EdgeKind.SEMANTIC] edge (used to
 *  scale both its pull in the layout and its opacity on screen); wikilinks
 *  always pull at full strength, so it is unused for them. */
data class GraphEdge(val fromIndex: Int, val toIndex: Int, val kind: EdgeKind, val weight: Float = 1f)
data class GraphLayoutResult(val nodes: List<GraphNode>, val edges: List<GraphEdge>)

/**
 * A small, dependency-free Fruchterman-Reingold force-directed layout: nodes
 * repel each other, edges pull their endpoints together, repeated for a fixed
 * number of iterations. Good enough for the note counts this app deals with
 * (hundreds, not tens of thousands) — no need for a real graph-layout library
 * for a single static-then-draggable view.
 */
object GraphLayout {
    /** Semantic edges pull weaker than a wikilink, so the wikilink structure
     *  still dominates the shape and similarity only nudges related notes closer. */
    private const val SEMANTIC_ATTRACTION_SCALE = 0.5f

    /**
     * [semanticLinks] are (noteIdA, noteIdB, cosine similarity) triples — by note
     * id, not index, since callers compute them independently of this layout.
     */
    fun compute(
        notes: List<GraphNoteData>,
        semanticLinks: List<Triple<Int, Int, Float>> = emptyList(),
        width: Float = 1000f,
        height: Float = 1000f,
        iterations: Int = 300
    ): GraphLayoutResult {
        if (notes.isEmpty()) return GraphLayoutResult(emptyList(), emptyList())

        val indexByTitle = notes.withIndex().associate { (i, n) -> n.title to i }
        val indexById = notes.withIndex().associate { (i, n) -> n.id to i }
        val edges = mutableListOf<GraphEdge>()
        val wikilinkPairs = HashSet<Pair<Int, Int>>()
        notes.forEachIndexed { i, note ->
            note.linkedTitles.forEach { linked ->
                indexByTitle[linked]?.let { j ->
                    if (j != i) {
                        edges += GraphEdge(i, j, EdgeKind.WIKILINK)
                        wikilinkPairs += unordered(i, j)
                    }
                }
            }
        }
        semanticLinks.forEach { (noteIdA, noteIdB, score) ->
            val i = indexById[noteIdA]
            val j = indexById[noteIdB]
            if (i != null && j != null && i != j && unordered(i, j) !in wikilinkPairs) {
                edges += GraphEdge(i, j, EdgeKind.SEMANTIC, weight = score)
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
                val attraction = if (e.kind == EdgeKind.SEMANTIC) e.weight * SEMANTIC_ATTRACTION_SCALE else 1f
                val force = (dist * dist) / k * attraction
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

    private fun unordered(a: Int, b: Int): Pair<Int, Int> = if (a < b) a to b else b to a
}
