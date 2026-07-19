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
  syncOk = Color(0xFF059669),
  syncPending = Color(0xFFD97706),
  localOnly = Color(0xFF6B7280),
  wikiLink = Color(0xFF4F46E5),
  hashtag = Color(0xFF4F46E5),
  code = Color(0xFFBE123C),
  codeBackground = Color(0xFFF3F4F6),
)

val DarkStatusColors = OctoStatusColors(
  syncOk = Color(0xFF34D399),
  syncPending = Color(0xFFFBBF24),
  localOnly = Color(0xFF9CA3AF),
  wikiLink = Color(0xFF818CF8),
  hashtag = Color(0xFF818CF8),
  code = Color(0xFFFDA4AF),
  codeBackground = Color(0xFF27272A),
)

val LocalOctoStatusColors = staticCompositionLocalOf { LightStatusColors }

/** Convenience accessor: `MaterialTheme.octoStatus.syncOk`. */
val MaterialTheme.octoStatus: OctoStatusColors
  @Composable
  @ReadOnlyComposable
  get() = LocalOctoStatusColors.current
