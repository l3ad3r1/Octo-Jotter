package com.l3ad3r1.octojotter.ai.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.l3ad3r1.octojotter.ai.AiContainer
import com.l3ad3r1.octojotter.ai.model.ModelCatalog
import com.l3ad3r1.octojotter.ai.model.ModelManager
import com.l3ad3r1.octojotter.ai.model.ModelStorage
import kotlinx.coroutines.launch

/**
 * DEBUG BUILDS ONLY. Exercises download-on-first-use and cross-app reuse:
 * shows whether the shared "AI Models" folder is accessible, whether the MiniLM
 * embedding bundle and each Hermes GGUF are already present, and lets you
 * download the embedding model with progress.
 */
class ModelManagerDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Scaffold { padding -> ModelManagerDebugScreen(Modifier.padding(padding)) }
        }
    }
}

@Composable
private fun ModelManagerDebugScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ai = remember { AiContainer.get(context) }
    val manager = ai.modelManager
    val storage = manager.storage

    var refresh by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("idle") }
    var progress by remember { mutableStateOf<Float?>(null) }

    // refresh is read so the presence checks re-run after actions.
    @Suppress("UNUSED_EXPRESSION") refresh

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Model Manager — debug", style = MaterialTheme.typography.titleMedium)
        Text(
            "Shared access (All-Files): ${storage.hasSharedAccess()}\n" +
                "Using: ${if (storage.usingSharedStorage) "shared /AI Models (Hermes-compatible)" else "app-private fallback"}\n" +
                "Chat dir: ${storage.chatModelsDir().absolutePath}\n" +
                "Embed dir: ${ai.modelDir.absolutePath}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Status: $status", style = MaterialTheme.typography.bodySmall)
        progress?.let { Text("Progress: ${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall) }

        Button(onClick = {
            runCatching { context.startActivity(ModelStorage.allFilesAccessIntent(context)) }
                .onFailure { status = "cannot open settings: ${it.message}" }
        }) { Text("Grant All-Files-Access (to share with Hermes)") }

        HorizontalDivider()

        val embed = ModelCatalog.EMBEDDING
        Text(
            "Embedding: ${embed.displayName} (${embed.model.sizeLabel} + ${embed.vocab.sizeLabel})\n" +
                "present: ${manager.isEmbeddingReady(embed)}",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = {
            scope.launch {
                status = "downloading embedding…"
                progress = 0f
                val r = manager.downloadEmbeddingModel(embed) { p -> progress = p.fraction }
                status = when (r) {
                    is ModelManager.Result.Success -> "embedding ready: ${r.file.absolutePath}"
                    is ModelManager.Result.AlreadyPresent -> "already present: ${r.file.absolutePath}"
                    is ModelManager.Result.Failure -> "failed: ${r.message}"
                }
                progress = null
                refresh++
            }
        }) { Text("Download embedding model") }

        HorizontalDivider()

        Text("Chat models (GGUF) — shared with Hermes:", style = MaterialTheme.typography.bodySmall)
        ModelCatalog.CHAT_MODELS.forEach { m ->
            Text(
                "• ${m.displayName} (${m.file.sizeLabel}) — present: ${manager.isChatModelPresent(m)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
