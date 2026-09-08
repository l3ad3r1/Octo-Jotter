package com.l3ad3r1.octojotter.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared layout values for the calm, paper-like Octo Jotter surfaces. */
object OctoSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object OctoShapes {
    val card = RoundedCornerShape(20.dp)
    val compactCard = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(50)
}

/**
 * [MaterialTheme.shapes] built from the same corner radii above, so a default
 * M3 component that isn't given an explicit `shape =` (dialogs, menus, text
 * fields, snackbars) still picks up Octo Jotter's own rounding instead of
 * Compose's stock M3 defaults. `medium`/`large` reuse [OctoShapes.compactCard]
 * /[.card] exactly, since those are the two corner radii the app already
 * established for "a card"; `extraSmall`/`small`/`extraLarge` fill in the
 * roles the app never had its own token for, using the M3 spec's own values.
 */
val OctoMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = OctoShapes.compactCard,
    large = OctoShapes.card,
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun octoLibrarySurface(): Color = MaterialTheme.colorScheme.surfaceContainerLow
