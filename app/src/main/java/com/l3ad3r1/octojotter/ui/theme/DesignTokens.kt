package com.l3ad3r1.octojotter.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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

@Composable
fun octoLibrarySurface(): Color = MaterialTheme.colorScheme.surfaceContainerLow
