package com.l3ad3r1.octojotter.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l3ad3r1.octojotter.ui.NoteViewModel

/**
 * Visualizes the [[wikilink]] network between notes — Obsidian's signature
 * feature. Layout runs once per screen visit (Fruchterman-Reingold, see
 * [GraphLayout]); panning/zooming is free after that since it's just a
 * transform on already-computed node positions, not a re-layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphViewScreen(
    viewModel: NoteViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (Int) -> Unit,
) {
    var layout by remember { mutableStateOf<GraphLayoutResult?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val notes = viewModel.getGraphData()
        layout = GraphLayout.compute(notes)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.2f, 4f)
        offset += panChange
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Graph View") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).testTag("graph_view_canvas")) {
            val current = layout
            if (current == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (current.nodes.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                    Text(
                        "No [[wikilinks]] yet — link two notes with [[Note title]] to see them here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val textMeasurer = rememberTextMeasurer()
                val primary = MaterialTheme.colorScheme.primary
                val outline = MaterialTheme.colorScheme.outlineVariant
                val onSurface = MaterialTheme.colorScheme.onSurface
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(transformState)
                        .pointerInput(current) {
                            detectTapGestures { tapOffset ->
                                val local = (tapOffset - offset) / scale
                                val hit = current.nodes.minByOrNull { node ->
                                    val dx = node.x - local.x
                                    val dy = node.y - local.y
                                    dx * dx + dy * dy
                                }
                                if (hit != null) {
                                    val dx = hit.x - local.x
                                    val dy = hit.y - local.y
                                    if (dx * dx + dy * dy < 40f * 40f) onNavigateToEditor(hit.id)
                                }
                            }
                        }
                ) {
                    withTransform({
                        translate(offset.x, offset.y)
                        scale(scale, scale, pivot = Offset.Zero)
                    }) {
                        current.edges.forEach { edge ->
                            val from = current.nodes[edge.fromIndex]
                            val to = current.nodes[edge.toIndex]
                            drawLine(
                                color = outline,
                                start = Offset(from.x, from.y),
                                end = Offset(to.x, to.y),
                                strokeWidth = 1.5f
                            )
                        }
                        current.nodes.forEach { node ->
                            val radius = (6 + node.degree * 1.5f).coerceAtMost(24f)
                            drawCircle(color = primary, radius = radius, center = Offset(node.x, node.y))
                            val layoutResult = textMeasurer.measure(
                                node.title,
                                style = TextStyle(color = onSurface, fontSize = 11.sp)
                            )
                            drawText(
                                layoutResult,
                                topLeft = Offset(node.x + radius + 4f, node.y - layoutResult.size.height / 2f)
                            )
                        }
                    }
                }
            }
        }
    }
}
