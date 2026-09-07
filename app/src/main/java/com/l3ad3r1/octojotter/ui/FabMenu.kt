package com.l3ad3r1.octojotter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The FAB's speed-dial: every "create a new X" shortcut collapses behind one
 * circular button instead of each one claiming its own permanent spot on the
 * bottom bar (or, worse, another stacked FAB per plugin). [onToday] and
 * [onFromTemplate] are null when their plugin isn't installed/has nothing to
 * offer, in which case that row simply doesn't exist — same convention as
 * every other plugin-gated entry point in this app.
 */
@Composable
fun NoteCreationFabMenu(
    onNewNote: () -> Unit,
    onNewTask: () -> Unit,
    onToday: (() -> Unit)?,
    onFromTemplate: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "fab_rotation")

    fun runAndCollapse(action: () -> Unit) {
        expanded = false
        action()
    }

    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onFromTemplate != null) {
                    FabMenuItem(
                        label = "From template",
                        icon = Icons.Default.Description,
                        testTag = "new_from_template_fab",
                        onClick = { runAndCollapse(onFromTemplate) }
                    )
                }
                if (onToday != null) {
                    FabMenuItem(
                        label = "Today",
                        icon = Icons.Default.CalendarToday,
                        testTag = "new_today_fab",
                        onClick = { runAndCollapse(onToday) }
                    )
                }
                FabMenuItem(
                    label = "New task",
                    icon = Icons.Default.CheckBox,
                    testTag = "new_task_fab",
                    onClick = { runAndCollapse(onNewTask) }
                )
                FabMenuItem(
                    label = "New note",
                    icon = Icons.Default.NoteAdd,
                    testTag = "new_note_fab_item",
                    onClick = { runAndCollapse(onNewNote) }
                )
            }
        }

        FloatingActionButton(
            onClick = {
                if (expanded) expanded = false else expanded = true
            },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.testTag("add_note_fab")
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = if (expanded) "Close menu" else "New…",
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}

@Composable
private fun FabMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.testTag(testTag)
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}
