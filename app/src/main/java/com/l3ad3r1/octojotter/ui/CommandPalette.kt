package com.l3ad3r1.octojotter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.l3ad3r1.octojotter.data.local.NoteEntity

/** One row in the command palette — either a fixed action or a matching note. */
private sealed interface PaletteItem {
    data class Action(val icon: ImageVector, val label: String, val onRun: () -> Unit) : PaletteItem
    data class OpenNote(val note: NoteEntity) : PaletteItem
}

/**
 * Obsidian/Notion-style quick-action overlay: type to filter fixed commands
 * (new note, today's note, toggle dark mode, jump to Ask AI/Graph/Settings)
 * and matching note titles, in one flat list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPalette(
    notes: List<NoteEntity>,
    onDismiss: () -> Unit,
    onOpenNote: (Int) -> Unit,
    onNewNote: () -> Unit,
    onOpenDailyNote: (() -> Unit)?,
    onOpenAskAi: (() -> Unit)?,
    onOpenGraphView: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    onToggleDarkMode: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val actions = remember(onOpenDailyNote, onOpenAskAi, onOpenGraphView) {
        buildList {
            add(PaletteItem.Action(Icons.Default.Add, "New note", onNewNote))
            onOpenDailyNote?.let { add(PaletteItem.Action(Icons.Default.CalendarToday, "Open today's note", it)) }
            onOpenAskAi?.let { add(PaletteItem.Action(Icons.Default.AutoAwesome, "Ask your notes", it)) }
            onOpenGraphView?.let { add(PaletteItem.Action(Icons.Default.Hub, "Open Graph View", it)) }
            add(PaletteItem.Action(Icons.Default.Settings, "Open Settings", onOpenSettings))
            add(PaletteItem.Action(Icons.Default.Description, "Toggle dark mode", onToggleDarkMode))
        }
    }

    val filtered = remember(query, notes, actions) {
        val q = query.trim()
        val matchingActions = actions.filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) }
        val matchingNotes = if (q.isEmpty()) {
            emptyList()
        } else {
            notes.filter {
                it.deletedAt == null &&
                    it.displayTitle.ifBlank { "Untitled Note" }.contains(q, ignoreCase = true)
            }
                .take(20)
                .map { PaletteItem.OpenNote(it) }
        }
        matchingActions + matchingNotes
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Jump to a note or run a command…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .focusRequester(focusRequester)
                        .testTag("command_palette_input")
                )
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(filtered) { item ->
                        when (item) {
                            is PaletteItem.Action -> ListItem(
                                headlineContent = { Text(item.label) },
                                leadingContent = { Icon(item.icon, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { item.onRun(); onDismiss() }
                                    .testTag("palette_action_${item.label}")
                            )
                            is PaletteItem.OpenNote -> ListItem(
                                headlineContent = { Text(item.note.displayTitle.ifBlank { "Untitled Note" }) },
                                supportingContent = {
                                    Text(
                                        item.note.content.take(60),
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                leadingContent = { Icon(Icons.Default.Description, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenNote(item.note.id); onDismiss() }
                                    .testTag("palette_note_${item.note.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}
