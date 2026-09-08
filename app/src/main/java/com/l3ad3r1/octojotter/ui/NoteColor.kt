package com.l3ad3r1.octojotter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.l3ad3r1.octojotter.data.local.NoteEntity

/**
 * Google Keep-style note color labels. Stored on [com.l3ad3r1.octojotter.data.local.NoteEntity.color]
 * as [id]; null means "no color" (the card's normal surface tone).
 *
 * Colors are fixed hues rather than derived from the Material color scheme —
 * the whole point is that they stay visually distinct from each other and
 * from the app's own primary/secondary containers, in both light and dark.
 *
 * Each [light]/[dark] container is a proper M3 tonal pair with its own
 * [onLight]/[onDark] text color — M3's rule is that a container may only be
 * combined with *its own* "on container" role, never a generic on-surface
 * gray (m3.material.io/styles/color/roles). A light-theme container is a
 * bright, saturated tone, so it pairs with a dark, hue-tinted text color; a
 * dark-theme container is a deep, saturated tone paired with a light one —
 * except yellow/green/teal, whose "dark" tier stays too light-reading for
 * white text (they're the highest-luminance hues, per HCT), so those keep
 * dark text even on the dark-theme card. Every pair below is hand-verified
 * to clear WCAG AA's 4.5:1 contrast ratio.
 */
enum class NoteColor(
    val id: String,
    val label: String,
    val light: Color,
    val onLight: Color,
    val dark: Color,
    val onDark: Color,
) {
    RED("red", "Red", Color(0xFFFF8A80), Color(0xFF7A1100), Color(0xFFC0392B), Color(0xFFFFFFFF)),
    ORANGE("orange", "Orange", Color(0xFFFFAB40), Color(0xFF5C3600), Color(0xFF8A4E00), Color(0xFFFFFFFF)),
    YELLOW("yellow", "Yellow", Color(0xFFFFD740), Color(0xFF4A3B00), Color(0xFFC9A227), Color(0xFF4A3B00)),
    GREEN("green", "Green", Color(0xFF69F0AE), Color(0xFF0B3D22), Color(0xFF2E9E5B), Color(0xFF052815)),
    TEAL("teal", "Teal", Color(0xFF64FFDA), Color(0xFF00382E), Color(0xFF1E9E8C), Color(0xFF001F1A)),
    BLUE("blue", "Blue", Color(0xFF82B1FF), Color(0xFF0A2B5C), Color(0xFF2F5CA8), Color(0xFFFFFFFF)),
    PURPLE("purple", "Purple", Color(0xFFB388FF), Color(0xFF2E0A5C), Color(0xFF7C4DCC), Color(0xFFFFFFFF)),
    PINK("pink", "Pink", Color(0xFFFF80AB), Color(0xFF5C0A2E), Color(0xFFB23A66), Color(0xFFFFFFFF));

    companion object {
        fun fromId(id: String?): NoteColor? = entries.firstOrNull { it.id == id }
    }
}

/** Same checkbox-line shape the Task Board matches, checked line-by-line
 *  (`TASK_BOARD_LINE_REGEX` in NoteApp.kt) — just testing for presence here. */
private val CHECKLIST_LINE_REGEX = Regex("""(?m)^\s*[-*]\s+\[[ xX]]""")

/**
 * The color a note's card shows. Four note properties get an automatic color,
 * checked in this priority order since a note can match more than one:
 * locked/encrypted, has a reminder, is the daily note, contains a checklist
 * item. That auto-detected color always wins over a manually picked one
 * ([NoteEntity.color], the Keep-style picker) for a note that matches one of
 * them; the manual picker still applies to every other note.
 */
fun NoteEntity.displayColor(): NoteColor? = when {
    locked || encrypted -> NoteColor.PURPLE
    reminderAt != null -> NoteColor.YELLOW
    isDailyNote -> NoteColor.BLUE
    CHECKLIST_LINE_REGEX.containsMatchIn(content) -> NoteColor.GREEN
    else -> NoteColor.fromId(color)
}

/** [NoteColor.light] or [.dark] depending on the current theme's brightness. */
@Composable
@ReadOnlyComposable
fun NoteColor.containerColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) dark else light

/** The text/icon color to pair with [containerColor] — [onLight] or [onDark]
 *  for the same theme. Never mix these across container/theme pairs. */
@Composable
@ReadOnlyComposable
fun NoteColor.onContainerColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) onDark else onLight

/** One tappable circular swatch in the note-color picker. */
@Composable
fun ColorSwatch(
    color: Color,
    selected: Boolean,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            .clickable(onClickLabel = contentDescription, onClick = onClick)
            .testTag(testTag)
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (color.luminance() < 0.5f) Color.White else Color.Black,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
