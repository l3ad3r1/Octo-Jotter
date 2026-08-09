package com.l3ad3r1.octojotter.ai.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.l3ad3r1.octojotter.ui.theme.MyApplicationTheme

/**
 * On-device RAG chat over the user's notes (Phase 2 of docs/ON-DEVICE-AI.md).
 * Retrieval + generation run entirely on-device; requires an arm64 device with
 * enough RAM. The chat model downloads on first use (or is reused from Hermes).
 */
class AiChatActivity : ComponentActivity() {
    private val viewModel: AiChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MyApplicationTheme { AiChatScreen(viewModel, onBack = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiChatScreen(viewModel: AiChatViewModel, onBack: () -> Unit) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val modelReady by viewModel.chatModelReady.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask your notes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                !viewModel.chatSupported -> InfoBox(
                    "On-device chat isn't available on this device. It needs a 64-bit ARM " +
                        "phone with enough memory."
                )
                !modelReady -> ModelGate(viewModel, download)
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (messages.isEmpty()) {
                            item { InfoBox("Ask a question and I'll answer from your notes — everything stays on your device.") }
                        }
                        items(messages) { msg -> MessageBubble(msg) }
                    }
                    ChatInput(enabled = !isGenerating, onSend = viewModel::ask)
                }
            }
        }
    }
}

@Composable
private fun ModelGate(viewModel: AiChatViewModel, download: AiChatViewModel.Download) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Enable on-device chat", style = MaterialTheme.typography.titleLarge)
        Text(
            "Download ${viewModel.chatModelName} (${viewModel.chatModelSizeLabel}, one time). " +
                "It runs entirely on your phone — your notes never leave the device.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        when (val d = download) {
            is AiChatViewModel.Download.InProgress -> {
                d.fraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                    ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    d.fraction?.let { "Downloading… ${(it * 100).toInt()}%" } ?: "Downloading…",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            is AiChatViewModel.Download.Failed -> {
                Text("Download failed: ${d.message}", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::downloadChatModel) { Text("Retry") }
            }
            else -> TextButton(onClick = viewModel::downloadChatModel) { Text("Download model") }
        }
    }
}

@Composable
private fun MessageBubble(msg: AiChatViewModel.Message) {
    val isUser = msg.role == AiChatViewModel.Message.Role.USER
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                val shown = msg.text.ifBlank { if (msg.streaming) "…" else "" }
                Text(shown, style = MaterialTheme.typography.bodyMedium)
                if (msg.streaming && msg.text.isNotBlank()) {
                    CircularProgressIndicator(Modifier.size(12.dp).padding(top = 6.dp), strokeWidth = 1.5.dp)
                }
                if (msg.citations.isNotEmpty()) {
                    Text(
                        "Sources",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                    msg.citations.forEach { c ->
                        AssistChip(
                            onClick = {},
                            label = { Text("Note ${c.noteId}: ${c.snippet.take(40)}") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInput(enabled: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        placeholder = { Text("Ask about your notes…") },
        trailingIcon = {
            IconButton(
                enabled = enabled && text.isNotBlank(),
                onClick = { onSend(text); text = "" },
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
        },
    )
}

@Composable
private fun InfoBox(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
