package com.l3ad3r1.octojotter.ai.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.l3ad3r1.octojotter.ai.model.ChatModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    viewModel: AiSettingsViewModel = viewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedChatModelId.collectAsStateWithLifecycle()
    val refreshTick by viewModel.refresh.collectAsStateWithLifecycle()

    // Re-read on-disk presence whenever the tick changes (returning from a grant,
    // finishing a download). Touch the value so recomposition depends on it.
    @Suppress("UNUSED_EXPRESSION") refreshTick

    val grantLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("On-device AI") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!viewModel.supportsAi) {
                SectionCard("Not supported") {
                    Text(
                        "On-device AI needs a 64-bit ARM phone with enough memory. " +
                            "Your device: ${viewModel.statusLine}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                return@Column
            }

            SectionCard("Status") {
                Text(viewModel.statusLine, style = MaterialTheme.typography.bodySmall)
                Text(
                    if (viewModel.usingSharedStorage())
                        "Models are shared with Hermes (shared \"AI Models\" folder)."
                    else "Models are stored privately to this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!viewModel.hasSharedAccess()) {
                SectionCard("Share models with Hermes") {
                    Text(
                        "Grant all-files access so Octo Jotter can reuse models already " +
                            "downloaded by the Hermes app (and vice-versa) instead of downloading again.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { grantLauncher.launch(viewModel.grantStorageAccessIntent()) }) {
                        Text("Grant access")
                    }
                }
            }

            SectionCard("Semantic search model") {
                val key = "embedding"
                Text(viewModel.embedding.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${viewModel.embedding.totalSizeLabel} · powers Smart search",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ModelAction(
                    present = viewModel.isEmbeddingReady(),
                    download = downloads[key],
                    onDownload = viewModel::downloadEmbedding,
                )
            }

            SectionCard("Chat model (LLM)") {
                Text(
                    "The model that answers in \"Ask your notes\". Downloads are one-time and shared with Hermes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                viewModel.chatModels.forEachIndexed { i, model ->
                    if (i > 0) HorizontalDivider()
                    ChatModelRow(
                        model = model,
                        selected = model.id == selectedId,
                        present = viewModel.isChatPresent(model),
                        download = downloads[model.id],
                        onSelect = { viewModel.selectChatModel(model.id) },
                        onDownload = { viewModel.downloadChat(model) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatModelRow(
    model: ChatModel,
    selected: Boolean,
    present: Boolean,
    download: AiSettingsViewModel.Download?,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    model.file.sizeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ModelAction(present = present, download = download, onDownload = onDownload, compact = true)
        }
    }
}

/** Shared present/download control for both embedding and chat rows. */
@Composable
private fun ModelAction(
    present: Boolean,
    download: AiSettingsViewModel.Download?,
    onDownload: () -> Unit,
    compact: Boolean = false,
) {
    when (val d = download) {
        is AiSettingsViewModel.Download.InProgress -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                d.fraction?.let { "  ${(it * 100).toInt()}%" } ?: "  …",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        is AiSettingsViewModel.Download.Failed -> Column(horizontalAlignment = Alignment.End) {
            Text("Failed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDownload) { Text("Retry") }
        }
        else -> if (present) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                if (!compact) Text("  Ready", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            TextButton(onClick = onDownload) { Text("Download") }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
