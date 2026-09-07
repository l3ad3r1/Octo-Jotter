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

/**
 * Google Keep-style note color labels. Stored on [com.l3ad3r1.octojotter.data.local.NoteEntity.color]
 * as [id]; null means "no color" (the card's normal surface tone).
 *
 * Colors are fixed hues rather than derived from the Material color scheme —
 * the whole point is that they stay visually distinct from each other and
 * from the app's own primary/secondary containers, in both light and dark.
 */
enum class NoteColor(val id: String, val label: String, val light: Color, val dark: Color) {
    RED("red", "Red", Color(0xFFFFDAD4), Color(0xFF5C2A22)),
    ORANGE("orange", "Orange", Color(0xFFFFE0B2), Color(0xFF5C3F17)),
    YELLOW("yellow", "Yellow", Color(0xFFFFF3B0), Color(0xFF544A17)),
    GREEN("green", "Green", Color(0xFFD7F2D2), Color(0xFF2A4A26)),
    TEAL("teal", "Teal", Color(0xFFCFF2ED), Color(0xFF1F4A45)),
    BLUE("blue", "Blue", Color(0xFFD4E4FF), Color(0xFF223A5C)),
    PURPLE("purple", "Purple", Color(0xFFE7D9FF), Color(0xFF412C5C)),
    PINK("pink", "Pink", Color(0xFFFFD9E8), Color(0xFF5C2440));

    companion object {
        fun fromId(id: String?): NoteColor? = entries.firstOrNull { it.id == id }
    }
}

/** [NoteColor.light] or [.dark] depending on the current theme's brightness. */
@Composable
@ReadOnlyComposable
fun NoteColor.containerColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) dark else light

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
