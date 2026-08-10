package com.l3ad3r1.octojotter.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.l3ad3r1.octojotter.data.markdown.Frontmatter
import com.l3ad3r1.octojotter.data.markdown.Frontmatter.PropertyValue
import com.l3ad3r1.octojotter.ui.theme.MonoFontFamily

/**
 * The note's YAML front matter, rendered the way Obsidian shows Properties:
 * a collapsible card of key/value rows above the note body, with lists as chips
 * and checkboxes as boxes.
 *
 * Read-only for now — editing writes through [Frontmatter]'s line-surgical
 * writers in a later phase.
 */
@Composable
fun PropertiesCard(
    frontmatter: Frontmatter,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true
) {
    if (frontmatter.fields.isEmpty()) return
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth().testTag("properties_card")
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("properties_card_header")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Properties",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${frontmatter.fields.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse properties" else "Expand properties",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    frontmatter.fields.forEach { (key, value) ->
                        PropertyRow(key, value)
                    }
                }
            }
        }
    }
}

/** The same properties in a dialog, for reading them while the editor hides the block. */
@Composable
fun PropertiesDialog(frontmatter: Frontmatter?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            if (frontmatter == null || frontmatter.fields.isEmpty()) {
                Text(
                    text = "This note has no properties. Add a YAML block at the very top of the " +
                        "note (between --- lines) and they will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    frontmatter.fields.forEach { (key, value) -> PropertyRow(key, value) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        modifier = Modifier.testTag("properties_dialog")
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropertyRow(key: String, value: PropertyValue) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("property_row_$key"),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        when (value) {
            is PropertyValue.Items -> {
                if (value.values.isEmpty()) {
                    EmptyValue()
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        value.values.forEach { item ->
                            AssistChip(
                                // Not clickable yet: front-matter tags are not in
                                // the tag index until the indexing phase lands, so
                                // tapping one would filter to nothing.
                                onClick = { },
                                label = { Text(item, style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }
            is PropertyValue.Checkbox -> Icon(
                imageVector = if (value.value) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (value.value) "Yes" else "No",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            is PropertyValue.Unparsed -> Text(
                text = value.raw.lines().drop(1).joinToString("\n") { it.trim() },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            is PropertyValue.Text ->
                if (value.value.isBlank()) EmptyValue()
                else ScalarText(value.value)
            is PropertyValue.Numeric -> ScalarText(
                if (value.value == value.value.toLong().toDouble()) {
                    value.value.toLong().toString()
                } else {
                    value.value.toString()
                }
            )
            is PropertyValue.DateStamp -> ScalarText(value.value)
        }
    }
}

@Composable
private fun ScalarText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun EmptyValue() {
    Text(
        text = "—",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
