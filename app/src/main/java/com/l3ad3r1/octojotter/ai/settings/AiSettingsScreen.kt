package com.l3ad3r1.octojotter.ai.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

    // Re-read on-disk presence whenever the tick changes (finishing a download).
    // Touch the value so recomposition depends on it.
    @Suppress("UNUSED_EXPRESSION") refreshTick

    // All Files Access is granted from system Settings, not an in-app dialog —
    // re-check on return so "Grant access" flips to "Granted" without a manual refresh.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                    "Downloads go to this app's private storage — no permission needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (viewModel.allFilesAccessSupported) {
                AllFilesAccessCard(hasAccess = viewModel.hasAllFilesAccess())
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
                    "The model that answers in \"Ask your notes\". Downloads are one-time.",
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
            Text(d.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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

/**
 * All Files Access is a heavy, redirect-flow permission (not a runtime
 * dialog) — [Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION] drops
 * the user into system Settings for this app, where they flip it on and come
 * back. Granting it lets the app also find a model sitting in the public
 * "AI Models" folder instead of only its own private one.
 */
@Composable
private fun AllFilesAccessCard(hasAccess: Boolean) {
    val context = LocalContext.current
    SectionCard("Find models in shared storage") {
        Text(
            if (hasAccess) {
                "Granted — models placed in the shared \"AI Models\" folder are detected too."
            } else {
                "Optional. Grant access to also detect a model placed by hand in the shared " +
                    "\"AI Models\" folder, instead of only this app's own private one."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasAccess) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text("  Granted", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            TextButton(onClick = {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        "package:${context.packageName}".toUri(),
                    )
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                }
                context.startActivity(intent)
            }) { Text("Grant access") }
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
