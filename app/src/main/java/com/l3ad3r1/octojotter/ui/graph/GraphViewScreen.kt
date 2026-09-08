package com.l3ad3r1.octojotter.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.l3ad3r1.octojotter.ai.graph.GraphAiViewModel
import com.l3ad3r1.octojotter.ui.NoteViewModel
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val NODE_HIT_RADIUS = 40f
private const val EDGE_HIT_RADIUS = 18f
private const val DIMMED_ALPHA = 0.25f
private val GRAPH_NODE_TOUCH_TARGET = 48.dp

/**
 * Visualizes the note network — Obsidian's signature feature, extended with a
 * semantic layer inspired by Kwipu (github.com/benmaster82/Kwipu), done fully
 * on-device: solid edges are explicit [[wikilinks]] (see [GraphLayout]); dashed
 * edges are notes the embedding index finds similar with no such link
 * ([GraphAiViewModel.semanticLinks]). Layout runs once per screen visit;
 * panning/zooming is free after that since it's just a transform on
 * already-computed node positions, not a re-layout.
 *
 * When on-device AI is enabled, tapping an edge asks the on-device model to
 * describe that relationship in one phrase, and the search action lets you ask
 * a grounded question over the notes — its cited notes are highlighted in the
 * graph. Both degrade to "not available" text rather than erroring when the
 * chat model hasn't been downloaded yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphViewScreen(
    viewModel: NoteViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (Int) -> Unit,
    graphAi: GraphAiViewModel = viewModel(),
) {
    val onDeviceAiEnabled by viewModel.onDeviceAiEnabled.collectAsStateWithLifecycle()

    var graphNotes by remember { mutableStateOf<List<GraphNoteData>>(emptyList()) }
    var layout by remember { mutableStateOf<GraphLayoutResult?>(null) }

    LaunchedEffect(onDeviceAiEnabled) {
        val notes = viewModel.getGraphData()
        graphNotes = notes
        val semanticLinks = if (onDeviceAiEnabled) {
            graphAi.semanticLinks().map { Triple(it.noteIdA, it.noteIdB, it.score) }
        } else {
            emptyList()
        }
        layout = GraphLayout.compute(notes, semanticLinks)
    }
    val notesById = remember(graphNotes) { graphNotes.associateBy { it.id } }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.2f, 4f)
        offset += panChange
    }

    var selectedEdge by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val relationLabels by graphAi.relationLabels.collectAsStateWithLifecycle()

    // Shared by the canvas's own pointer-tap detection and by each node's
    // accessibility custom action below, so a screen reader can trigger the
    // exact same relation-labeling flow a sighted tap on the edge does.
    fun selectEdge(fromId: Int, toId: Int) {
        selectedEdge = fromId to toId
        val noteA = notesById[fromId]
        val noteB = notesById[toId]
        if (noteA != null && noteB != null && !noteA.locked && !noteB.locked) {
            graphAi.labelRelation(
                GraphAiViewModel.RelationNote(noteA.id, noteA.title, noteA.content),
                GraphAiViewModel.RelationNote(noteB.id, noteB.title, noteB.content),
            )
        }
    }

    var queryBarVisible by remember { mutableStateOf(false) }
    var queryText by remember { mutableStateOf("") }
    val queryState by graphAi.queryState.collectAsStateWithLifecycle()
    val highlightedNoteIds = remember(queryState) {
        when (val s = queryState) {
            is GraphAiViewModel.QueryState.Answering -> s.citations.map { it.noteId }.toSet()
            is GraphAiViewModel.QueryState.Done -> s.citations.map { it.noteId }.toSet()
            else -> emptySet()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Graph View") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (onDeviceAiEnabled) {
                        IconButton(
                            onClick = { queryBarVisible = !queryBarVisible },
                            modifier = Modifier.testTag("graph_view_ask_button"),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Ask a question")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (queryBarVisible) {
                QueryBar(
                    text = queryText,
                    onTextChange = { queryText = it },
                    onSend = { graphAi.ask(queryText) },
                    onClose = {
                        queryBarVisible = false
                        queryText = ""
                        graphAi.clearQuery()
                    },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth().testTag("graph_view_canvas")) {
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
                    val semanticColor = MaterialTheme.colorScheme.tertiary
                    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) }
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .transformable(transformState)
                            .pointerInput(current, onDeviceAiEnabled) {
                                detectTapGestures { tapOffset ->
                                    val local = (tapOffset - offset) / scale
                                    val hitNode = current.nodes.minByOrNull { node ->
                                        val dx = node.x - local.x
                                        val dy = node.y - local.y
                                        dx * dx + dy * dy
                                    }?.takeIf { node ->
                                        val dx = node.x - local.x
                                        val dy = node.y - local.y
                                        dx * dx + dy * dy < NODE_HIT_RADIUS * NODE_HIT_RADIUS
                                    }
                                    if (hitNode != null) {
                                        onNavigateToEditor(hitNode.id)
                                        return@detectTapGestures
                                    }
                                    if (!onDeviceAiEnabled || current.edges.isEmpty()) return@detectTapGestures
                                    val hitEdge = current.edges.minByOrNull { edge ->
                                        val from = current.nodes[edge.fromIndex]
                                        val to = current.nodes[edge.toIndex]
                                        distanceToSegment(local, Offset(from.x, from.y), Offset(to.x, to.y))
                                    } ?: return@detectTapGestures
                                    val from = current.nodes[hitEdge.fromIndex]
                                    val to = current.nodes[hitEdge.toIndex]
                                    if (distanceToSegment(local, Offset(from.x, from.y), Offset(to.x, to.y)) > EDGE_HIT_RADIUS) {
                                        return@detectTapGestures
                                    }
                                    selectEdge(from.id, to.id)
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
                                if (edge.kind == EdgeKind.SEMANTIC) {
                                    drawLine(
                                        color = semanticColor.copy(alpha = (0.25f + edge.weight * 0.5f).coerceIn(0.25f, 0.85f)),
                                        start = Offset(from.x, from.y),
                                        end = Offset(to.x, to.y),
                                        strokeWidth = 1.3f,
                                        pathEffect = dashEffect,
                                    )
                                } else {
                                    drawLine(
                                        color = outline,
                                        start = Offset(from.x, from.y),
                                        end = Offset(to.x, to.y),
                                        strokeWidth = 1.5f
                                    )
                                }
                            }
                            current.nodes.forEach { node ->
                                val radius = (6 + node.degree * 1.5f).coerceAtMost(24f)
                                val dimmed = highlightedNoteIds.isNotEmpty() && node.id !in highlightedNoteIds
                                drawCircle(
                                    color = primary.copy(alpha = if (dimmed) DIMMED_ALPHA else 1f),
                                    radius = radius,
                                    center = Offset(node.x, node.y)
                                )
                                if (node.id in highlightedNoteIds) {
                                    drawCircle(
                                        color = semanticColor,
                                        radius = radius + 5f,
                                        center = Offset(node.x, node.y),
                                        style = Stroke(width = 3f),
                                    )
                                }
                                val layoutResult = textMeasurer.measure(
                                    node.title,
                                    style = TextStyle(
                                        color = onSurface.copy(alpha = if (dimmed) DIMMED_ALPHA else 1f),
                                        fontSize = 11.sp,
                                    )
                                )
                                drawText(
                                    layoutResult,
                                    topLeft = Offset(node.x + radius + 4f, node.y - layoutResult.size.height / 2f)
                                )
                            }
                        }
                    }
                    // Accessibility overlay: the graph above is otherwise a raw Canvas —
                    // invisible to TalkBack, since drawCircle/drawText carry no semantics
                    // of their own. One semantics-only Box per node, positioned with the
                    // same node*scale+offset transform the canvas and its tap detector
                    // use, exposes each note as a focusable, clickable element with the
                    // note's title, plus one custom action per edge touching it so a
                    // screen-reader user can reach relation-labeling too — without a
                    // `.clickable`/`.pointerInput` of its own, these boxes carry no
                    // pointer input node, so they're transparent to real touch/mouse and
                    // never compete with the canvas's own gesture handling.
                    val density = LocalDensity.current
                    val touchTargetPx = with(density) { GRAPH_NODE_TOUCH_TARGET.toPx() }
                    current.nodes.forEachIndexed { index, node ->
                        val screenX = node.x * scale + offset.x
                        val screenY = node.y * scale + offset.y
                        val edgeActions = current.edges.mapNotNull { edge ->
                            val otherIndex = when (index) {
                                edge.fromIndex -> edge.toIndex
                                edge.toIndex -> edge.fromIndex
                                else -> return@mapNotNull null
                            }
                            val other = current.nodes[otherIndex]
                            CustomAccessibilityAction("Relationship with ${other.title}") {
                                selectEdge(node.id, other.id)
                                true
                            }
                        }
                        Box(
                            modifier = Modifier
                                .offset { IntOffset((screenX - touchTargetPx / 2f).roundToInt(), (screenY - touchTargetPx / 2f).roundToInt()) }
                                .size(GRAPH_NODE_TOUCH_TARGET)
                                .testTag("graph_node_${node.id}")
                                .semantics {
                                    contentDescription = node.title
                                    onClick("Open note") { onNavigateToEditor(node.id); true }
                                    if (edgeActions.isNotEmpty()) customActions = edgeActions
                                }
                        )
                    }
                    if (current.edges.any { it.kind == EdgeKind.SEMANTIC }) {
                        GraphLegend(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
                    }
                }

                if (queryState !is GraphAiViewModel.QueryState.Idle) {
                    QueryAnswerCard(
                        state = queryState,
                        onCitationClick = onNavigateToEditor,
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                    )
                }

                selectedEdge?.let { (idA, idB) ->
                    val noteA = notesById[idA]
                    val noteB = notesById[idB]
                    if (noteA != null && noteB != null) {
                        val label = when {
                            noteA.locked || noteB.locked ->
                                GraphAiViewModel.RelationLabel.Failed("Relationship labeling isn't available for locked notes.")
                            else -> relationLabels[GraphAiViewModel.relationKey(idA, idB)]
                        }
                        RelationLabelCard(
                            titleA = noteA.title,
                            titleB = noteB.title,
                            label = label,
                            onDismiss = { selectedEdge = null },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Distance from point [p] to the segment [a]-[b]. */
private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val lengthSq = abx * abx + aby * aby
    val t = if (lengthSq > 0f) (((p.x - a.x) * abx + (p.y - a.y) * aby) / lengthSq).coerceIn(0f, 1f) else 0f
    val dx = p.x - (a.x + abx * t)
    val dy = p.y - (a.y + aby * t)
    return sqrt(dx * dx + dy * dy)
}

@Composable
private fun GraphLegend(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text("— Linked", style = MaterialTheme.typography.labelSmall)
            Text("- - Similar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun QueryBar(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f).testTag("graph_view_query_field"),
            placeholder = { Text("Ask a question about your notes…") },
            singleLine = true,
        )
        IconButton(onClick = onSend, enabled = text.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask")
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}

@Composable
private fun QueryAnswerCard(
    state: GraphAiViewModel.QueryState,
    onCitationClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            when (state) {
                is GraphAiViewModel.QueryState.Answering ->
                    Text(state.text.ifBlank { "Thinking…" }, style = MaterialTheme.typography.bodyMedium)
                is GraphAiViewModel.QueryState.Done -> {
                    Text(state.text, style = MaterialTheme.typography.bodyMedium)
                    if (state.citations.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.citations.forEach { c ->
                                AssistChip(onClick = { onCitationClick(c.noteId) }, label = { Text(c.snippet.take(24)) })
                            }
                        }
                    }
                }
                is GraphAiViewModel.QueryState.Error -> Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                GraphAiViewModel.QueryState.Idle -> {}
            }
        }
    }
}

@Composable
private fun RelationLabelCard(
    titleA: String,
    titleB: String,
    label: GraphAiViewModel.RelationLabel?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$titleA ↔ $titleB", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                }
            }
            when (label) {
                null, GraphAiViewModel.RelationLabel.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Thinking…", style = MaterialTheme.typography.bodyMedium)
                }
                is GraphAiViewModel.RelationLabel.Ready -> Text(label.text, style = MaterialTheme.typography.bodyMedium)
                is GraphAiViewModel.RelationLabel.Failed -> Text(
                    label.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
