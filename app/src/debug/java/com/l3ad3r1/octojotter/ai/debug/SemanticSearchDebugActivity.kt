package com.l3ad3r1.octojotter.ai.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.l3ad3r1.octojotter.ai.AiContainer
import com.l3ad3r1.octojotter.ai.search.SearchResult
import kotlinx.coroutines.launch

/**
 * DEBUG BUILDS ONLY. Validates the Phase 1 semantic-search pipeline end-to-end
 * on a real device: reindex all notes, then run a hybrid query and inspect the
 * ranked results. Uses the real ONNX MiniLM embedder if its model + vocab are on
 * disk, otherwise the deterministic bag-of-words fallback (labelled below).
 */
class SemanticSearchDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Scaffold { padding -> SemanticSearchDebugScreen(Modifier.padding(padding)) }
        }
    }
}

@Composable
private fun SemanticSearchDebugScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ai = remember { AiContainer.get(context) }

    var status by remember { mutableStateOf("idle") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Semantic Search — Phase 1 debug", style = MaterialTheme.typography.titleMedium)
        Text(ai.capability.describe(), style = MaterialTheme.typography.bodySmall)
        Text(
            "Embedder: ${if (ai.useRealEmbedder) "ONNX MiniLM (real)" else "bag-of-words fallback"}\n" +
                "Model dir: ${ai.modelDir.absolutePath}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Status: $status", style = MaterialTheme.typography.bodySmall)

        Button(onClick = {
            scope.launch {
                status = "indexing…"
                val r = runCatching { ai.indexer().indexAll() }
                status = r.fold(
                    { "indexed=${it.indexed} skipped=${it.skipped} removed=${it.removed}" },
                    { "index failed: ${it.message}" },
                )
            }
        }) { Text("Reindex all notes") }

        HorizontalDivider()

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Query") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            scope.launch {
                status = "searching…"
                val r = runCatching { ai.search().search(query, k = 10) }
                r.onSuccess { results = it; status = "${it.size} results" }
                    .onFailure { status = "search failed: ${it.message}" }
            }
        }) { Text("Search") }

        results.forEach { hit ->
            Text(
                "note ${hit.noteId}  score=${"%.3f".format(hit.score)}  " +
                    "sem=${"%.3f".format(hit.semanticScore)}  kw=${hit.keywordMatch}\n" +
                    (hit.bestChunk?.take(160) ?: "(no chunk)"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}
