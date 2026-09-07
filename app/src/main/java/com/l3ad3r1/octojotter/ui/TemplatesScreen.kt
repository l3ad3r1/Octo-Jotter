package com.l3ad3r1.octojotter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.l3ad3r1.octojotter.data.local.TemplateEntity

/**
 * CRUD for the Templates plugin's note templates. `{{date}}`, `{{time}}`, and
 * `{{title}}` in a template's content are substituted when a note is created
 * from it; a template literally named "Daily" also seeds the Daily Notes
 * plugin's note, so users get one obvious way to customize it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    viewModel: NoteViewModel,
    onNavigateBack: () -> Unit,
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TemplateEntity?>(null) }
    var showNewDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TemplateEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Templates") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewDialog = true },
                modifier = Modifier.testTag("add_template_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New template")
            }
        }
    ) { padding ->
        if (templates.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
            ) {
                Text(
                    "No templates yet. Add one — a template named \"Daily\" also " +
                        "seeds new Daily Notes automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(templates, key = { it.id }) { template ->
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("template_row_${template.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier.weight(1f).clickable { editing = template }
                            ) {
                                Text(template.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    template.content.take(80),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { pendingDelete = template }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete template")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        TemplateEditDialog(
            initial = null,
            onDismiss = { showNewDialog = false },
            onSave = { name, content ->
                viewModel.saveTemplate(null, name, content)
                showNewDialog = false
            }
        )
    }
    editing?.let { template ->
        TemplateEditDialog(
            initial = template,
            onDismiss = { editing = null },
            onSave = { name, content ->
                viewModel.saveTemplate(template.id, name, content)
                editing = null
            }
        )
    }
    pendingDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${template.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTemplate(template.id)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TemplateEditDialog(
    initial: TemplateEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, content: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New template" else "Edit template") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("template_name_input")
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    placeholder = { Text("Use {{date}}, {{time}}, {{title}}…") },
                    modifier = Modifier.fillMaxWidth().height(180.dp).testTag("template_content_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, content) },
                modifier = Modifier.testTag("save_template_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
