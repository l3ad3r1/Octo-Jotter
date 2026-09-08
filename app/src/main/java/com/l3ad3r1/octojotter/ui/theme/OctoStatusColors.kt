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
  val highlight: Color,
  val highlightBackground: Color,
  // Badge pairs for SyncStatusIndicator: a container fill plus its own text
  // color, both theme-aware. Deliberately not reusing syncOk/syncPending —
  // those are tuned as plain text against the app's normal surface, not
  // against their own tinted container.
  val syncedContainer: Color,
  val onSyncedContainer: Color,
  val syncingContainer: Color,
  val onSyncingContainer: Color,
  // highlightMarkdown() heading levels (H1 > H2 > H3, most to least saturated).
  val heading1: Color,
  val heading2: Color,
  val heading3: Color,
)

val LightStatusColors = OctoStatusColors(
  syncOk = Color(0xFF059669),
  syncPending = Color(0xFFD97706),
  localOnly = Color(0xFF6B7280),
  wikiLink = Color(0xFF275B71),
  hashtag = Color(0xFF275B71),
  code = Color(0xFFBE123C),
  codeBackground = Color(0xFFF3F4F6),
  highlight = Color(0xFF422006),
  highlightBackground = Color(0xFFFEF08A),
  syncedContainer = Color(0xFFECFDF5),
  onSyncedContainer = Color(0xFF065F46),
  syncingContainer = Color(0xFFEFF6FF),
  onSyncingContainer = Color(0xFF1D4ED8),
  heading1 = Color(0xFF1D4ED8),
  heading2 = Color(0xFF0369A1),
  heading3 = Color(0xFF0E7490),
)

val DarkStatusColors = OctoStatusColors(
  syncOk = Color(0xFF34D399),
  syncPending = Color(0xFFFBBF24),
  localOnly = Color(0xFF9CA3AF),
  wikiLink = Color(0xFF6FC7E0),
  hashtag = Color(0xFF6FC7E0),
  code = Color(0xFFFDA4AF),
  codeBackground = Color(0xFF27272A),
  highlight = Color(0xFF1C1917),
  highlightBackground = Color(0xFFCA8A04),
  syncedContainer = Color(0xFF022C22),
  onSyncedContainer = Color(0xFF6EE7B7),
  syncingContainer = Color(0xFF172554),
  onSyncingContainer = Color(0xFF93C5FD),
  heading1 = Color(0xFF60A5FA),
  heading2 = Color(0xFF38BDF8),
  heading3 = Color(0xFF22D3EE),
)

val LocalOctoStatusColors = staticCompositionLocalOf { LightStatusColors }

/** Convenience accessor: `MaterialTheme.octoStatus.syncOk`. */
val MaterialTheme.octoStatus: OctoStatusColors
  @Composable
  @ReadOnlyComposable
  get() = LocalOctoStatusColors.current
