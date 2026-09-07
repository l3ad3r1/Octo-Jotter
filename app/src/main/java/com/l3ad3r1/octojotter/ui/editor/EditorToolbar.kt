package com.l3ad3r1.octojotter.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertLink
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Subscript
import androidx.compose.material.icons.filled.Superscript
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Markdown editor's formatting bar.
 *
 * A single horizontally-scrolling row of compact icon buttons split into four
 * groups by hairline dividers — text, insert, blocks, edit — with the formats
 * that currently apply at the caret drawn as filled pills (see
 * [activeInlineFormats] / [activeBlockFormat]). Every action is a pure
 * transform from [MarkdownEditing], so the bar itself holds no editing state.
 */
@Composable
fun EditorToolbar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onPickImage: () -> Unit,
    onDraw: () -> Unit,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    monospace: Boolean,
    onMonospaceChange: (Boolean) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    canRedo: Boolean,
    onRedo: () -> Unit,
    pluginActions: List<PluginAction>,
    onPluginAction: (PluginAction) -> Unit,
    // Scan Text (OCR) plugin — null (the default) when it's not installed, so
    // the button simply doesn't exist rather than showing disabled.
    onScanText: (() -> Unit)? = null,
    scanBusy: Boolean = false,
    modifier: Modifier = Modifier
) {
    val activeInline = remember(value.text, value.selection) { activeInlineFormats(value) }
    val activeBlock = remember(value.text, value.selection) { activeBlockFormat(value) }

    fun apply(transform: (TextFieldValue) -> TextFieldValue) = onValueChange(transform(value))

    // The activity is edge-to-edge, so the bar has to clear the navigation bar
    // itself — and ride above the keyboard once it opens. Union rather than
    // chaining the two, or the insets double-count while typing. That padding
    // goes on the outer box; the visual bar floats inside it as its own docked,
    // elevated pill rather than a strip flush with the screen edge — the M3
    // Expressive "docked toolbar" pattern.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 6.dp,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ---- Text -------------------------------------------------
                ToolbarButton(
                    icon = Icons.Default.FormatBold,
                    label = "Bold",
                    active = InlineFormat.Bold in activeInline,
                    testTag = "format_bold_button"
                ) { apply { toggleInline(it, InlineFormat.Bold) } }

                ToolbarButton(
                    icon = Icons.Default.FormatItalic,
                    label = "Italic",
                    active = InlineFormat.Italic in activeInline,
                    testTag = "format_italic_button"
                ) { apply { toggleInline(it, InlineFormat.Italic) } }

                ToolbarButton(
                    icon = Icons.Default.FormatStrikethrough,
                    label = "Strikethrough",
                    active = InlineFormat.Strikethrough in activeInline,
                    testTag = "format_strikethrough_button"
                ) { apply { toggleInline(it, InlineFormat.Strikethrough) } }

                ToolbarButton(
                    icon = Icons.Default.Superscript,
                    label = "Superscript",
                    active = InlineFormat.Superscript in activeInline,
                    testTag = "format_superscript_button"
                ) { apply { toggleInline(it, InlineFormat.Superscript) } }

                ToolbarButton(
                    icon = Icons.Default.Subscript,
                    label = "Subscript",
                    active = InlineFormat.Subscript in activeInline,
                    testTag = "format_subscript_button"
                ) { apply { toggleInline(it, InlineFormat.Subscript) } }

                ToolbarButton(
                    icon = Icons.Default.FormatColorFill,
                    label = "Highlight",
                    active = InlineFormat.Highlight in activeInline,
                    testTag = "format_highlight_button"
                ) { apply { toggleInline(it, InlineFormat.Highlight) } }

                TypographyMenu(
                    activeBlock = activeBlock,
                    fontSize = fontSize,
                    onFontSizeChange = onFontSizeChange,
                    monospace = monospace,
                    onMonospaceChange = onMonospaceChange,
                    onHeading = { format -> apply { toggleBlock(it, format) } }
                )

                ToolbarDivider()

                // ---- Insert -----------------------------------------------
                ToolbarButton(
                    icon = Icons.Default.InsertLink,
                    label = "Link",
                    testTag = "format_link_button"
                ) { apply { insertLink(it) } }

                ToolbarButton(
                    icon = Icons.Default.Image,
                    label = "Add image",
                    testTag = "add_image_button",
                    onClick = onPickImage
                )

                ToolbarButton(
                    icon = Icons.Default.Draw,
                    label = "Insert drawing",
                    testTag = "add_drawing_button",
                    onClick = onDraw
                )

                if (onScanText != null) {
                    ToolbarButton(
                        icon = Icons.Default.DocumentScanner,
                        label = "Scan text",
                        enabled = !scanBusy,
                        testTag = "scan_text_button",
                        onClick = onScanText
                    )
                }

                ToolbarButton(
                    icon = Icons.Default.TableChart,
                    label = "Table",
                    testTag = "format_table_button"
                ) { apply { insertBlockSnippet(it, MarkdownSnippets.TABLE) } }

                ToolbarButton(
                    icon = Icons.Default.HorizontalRule,
                    label = "Divider",
                    testTag = "format_rule_button"
                ) { apply { insertBlockSnippet(it, MarkdownSnippets.HORIZONTAL_RULE) } }

                ToolbarButton(
                    icon = Icons.Default.Today,
                    label = "Insert date",
                    testTag = "insert_date_button"
                ) { apply { insertInline(it, todayStamp()) } }

                ToolbarDivider()

                // ---- Blocks -----------------------------------------------
                ToolbarButton(
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    label = "Bulleted list",
                    active = activeBlock == BlockFormat.Bullet,
                    testTag = "format_list_button"
                ) { apply { toggleBlock(it, BlockFormat.Bullet) } }

                ToolbarButton(
                    icon = Icons.Default.FormatListNumbered,
                    label = "Numbered list",
                    active = activeBlock == BlockFormat.Ordered,
                    testTag = "format_ordered_list_button"
                ) { apply { toggleBlock(it, BlockFormat.Ordered) } }

                ToolbarButton(
                    icon = Icons.Default.CheckBox,
                    label = "Task list",
                    active = activeBlock == BlockFormat.Task,
                    testTag = "format_task_list_button"
                ) { apply { toggleBlock(it, BlockFormat.Task) } }

                CalloutMenu { kind -> apply { insertBlockSnippet(it, MarkdownSnippets.callout(kind)) } }

                ToolbarButton(
                    icon = Icons.Default.FormatQuote,
                    label = "Quote",
                    active = activeBlock == BlockFormat.Quote,
                    testTag = "format_quote_button"
                ) { apply { toggleBlock(it, BlockFormat.Quote) } }

                ToolbarButton(
                    icon = Icons.Default.Code,
                    label = "Inline code",
                    active = InlineFormat.Code in activeInline,
                    testTag = "format_code_button"
                ) { apply { toggleInline(it, InlineFormat.Code) } }

                ToolbarButton(
                    icon = Icons.Default.DataObject,
                    label = "Code block",
                    testTag = "format_code_block_button"
                ) { apply { insertBlockSnippet(it, MarkdownSnippets.CODE_BLOCK) } }

                ToolbarDivider()

                // ---- Edit -------------------------------------------------
                ToolbarButton(
                    icon = Icons.AutoMirrored.Filled.FormatIndentIncrease,
                    label = "Indent",
                    testTag = "format_indent_button"
                ) { apply { indent(it) } }

                ToolbarButton(
                    icon = Icons.AutoMirrored.Filled.FormatIndentDecrease,
                    label = "Outdent",
                    testTag = "format_outdent_button"
                ) { apply { outdent(it) } }

                ToolbarButton(
                    icon = Icons.Default.Undo,
                    label = "Undo",
                    enabled = canUndo,
                    testTag = "editor_undo_button",
                    onClick = onUndo
                )

                ToolbarButton(
                    icon = Icons.Default.Redo,
                    label = "Redo",
                    enabled = canRedo,
                    testTag = "editor_redo_button",
                    onClick = onRedo
                )

                if (pluginActions.isNotEmpty()) {
                    ToolbarDivider()
                    PluginMenu(pluginActions, onPluginAction)
                }
            }
        }
    }
}

/** A plugin-contributed toolbar entry — either a script command or a text snippet. */
data class PluginAction(
    val id: String,
    val name: String,
    val isCommand: Boolean
)

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    testTag: String,
    onClick: () -> Unit
) {
    ToolbarSlot(active = active, enabled = enabled, label = label, testTag = testTag, onClick = onClick) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

/** Shared 36dp tap target: transparent normally, filled pill when [active]. */
@Composable
private fun ToolbarSlot(
    active: Boolean,
    enabled: Boolean,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val background = if (active) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .testTag(testTag)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides tint) {
            content()
        }
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .width(1.dp)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}

/** The "font bar": heading levels plus the editor's own type size and face. */
@Composable
private fun TypographyMenu(
    activeBlock: BlockFormat?,
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    monospace: Boolean,
    onMonospaceChange: (Boolean) -> Unit,
    onHeading: (BlockFormat) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolbarSlot(
            active = activeBlock?.isHeading == true || expanded,
            enabled = true,
            label = "Text style",
            testTag = "format_text_style_button",
            onClick = { expanded = true }
        ) {
            Icon(Icons.Default.FormatSize, contentDescription = "Text style", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                BlockFormat.H1 to 24,
                BlockFormat.H2 to 20,
                BlockFormat.H3 to 17
            ).forEach { (format, size) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Heading ${format.prefix.count { it == '#' }}",
                            fontSize = size.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    trailingIcon = { if (activeBlock == format) Text("•", fontWeight = FontWeight.Bold) },
                    onClick = {
                        expanded = false
                        onHeading(format)
                    },
                    modifier = Modifier.testTag("format_heading_${format.name.lowercase()}")
                )
            }
            HorizontalDivider()

            // Editor type size — applies to the editing surface, not the note.
            DropdownMenuItem(
                text = { Text("Editor text size", style = MaterialTheme.typography.labelLarge) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepperGlyph("A", 13, "decrease_font_size") {
                            onFontSizeChange(fontSize - 1)
                        }
                        Text(
                            text = "$fontSize",
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(28.dp)
                        )
                        StepperGlyph("A", 19, "increase_font_size") {
                            onFontSizeChange(fontSize + 1)
                        }
                    }
                },
                onClick = { }
            )
            DropdownMenuItem(
                text = { Text("Monospace body") },
                trailingIcon = { Text(if (monospace) "On" else "Off", fontWeight = FontWeight.Bold) },
                onClick = {
                    onMonospaceChange(!monospace)
                },
                modifier = Modifier.testTag("toggle_monospace")
            )
        }
    }
}

@Composable
private fun StepperGlyph(text: String, size: Int, testTag: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(text = text, fontSize = size.sp, fontWeight = FontWeight.Bold)
    }
}

/** Obsidian-style callouts: `> [!NOTE]`, `> [!WARNING]`, … */
@Composable
private fun CalloutMenu(onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolbarSlot(
            active = expanded,
            enabled = true,
            label = "Callout",
            testTag = "format_callout_button",
            onClick = { expanded = true }
        ) {
            Icon(Icons.Default.Info, contentDescription = "Callout", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION").forEach { kind ->
                DropdownMenuItem(
                    text = { Text(kind.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        expanded = false
                        onPick(kind)
                    },
                    modifier = Modifier.testTag("callout_${kind.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun PluginMenu(actions: List<PluginAction>, onAction: (PluginAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolbarSlot(
            active = expanded,
            enabled = true,
            label = "Plugin commands",
            testTag = "plugin_commands_button",
            onClick = { expanded = true }
        ) {
            Icon(Icons.Default.Extension, contentDescription = "Plugin commands", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.name) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (action.isCommand) Icons.Default.Bolt else Icons.Default.Bookmark,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        expanded = false
                        onAction(action)
                    },
                    modifier = Modifier.testTag("plugin_action_${action.id}")
                )
            }
        }
    }
}

/** `2026-07-28` — matches the daily-note convention the Second Brain plugins use. */
private fun todayStamp(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
