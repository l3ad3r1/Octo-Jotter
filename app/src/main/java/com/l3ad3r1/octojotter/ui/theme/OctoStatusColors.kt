package com.l3ad3r1.octojotter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors that aren't part of the standard Material color scheme
 * (sync states, markdown accents). Defined per-theme so every pairing meets
 * WCAG AA contrast in both light and dark mode instead of using hardcoded hex
 * that only looked right on a white background.
 */
data class OctoStatusColors(
  val syncOk: Color,
  val syncPending: Color,
  val localOnly: Color,
  val wikiLink: Color,
  val hashtag: Color,
  val code: Color,
  val codeBackground: Color,
)

val LightStatusColors = OctoStatusColors(
  syncOk = Color(0xFF2E7D32),
  syncPending = Color(0xFF8A5000),
  localOnly = Color(0xFF5F6368),
  wikiLink = Color(0xFF1565C0),
  hashtag = Color(0xFF8A5000),
  code = Color(0xFFB3123C),
  codeBackground = Color(0xFFEFEFF2),
)

val DarkStatusColors = OctoStatusColors(
  syncOk = Color(0xFF7FD98A),
  syncPending = Color(0xFFFFB77C),
  localOnly = Color(0xFFB0B4BB),
  wikiLink = Color(0xFF9DCAFF),
  hashtag = Color(0xFFFFB77C),
  code = Color(0xFFFF9EB1),
  codeBackground = Color(0xFF2A2D31),
)

val LocalOctoStatusColors = staticCompositionLocalOf { LightStatusColors }

/** Convenience accessor: `MaterialTheme.octoStatus.syncOk`. */
val MaterialTheme.octoStatus: OctoStatusColors
  @Composable
  @ReadOnlyComposable
  get() = LocalOctoStatusColors.current
