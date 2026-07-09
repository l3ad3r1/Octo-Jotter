package com.l3ad3r1.octojotter.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Inkwell typography
// The design pairs three roles (src/index.css @theme):
//   serif = Libre Baskerville  → titles, headings, brand   (FontFamily.Serif)
//   sans  = Inter              → UI / body / labels        (FontFamily.SansSerif)
//   mono  = JetBrains Mono     → the markdown editor body   (MonoFontFamily)
// System families stand in for the branded fonts to keep the character without
// bundling font binaries; swap to res/font TTFs later for a pixel-exact match.
// ---------------------------------------------------------------------------

val SerifFontFamily = FontFamily.Serif
val SansFontFamily = FontFamily.SansSerif
val MonoFontFamily = FontFamily.Monospace

val Typography =
  Typography(
    // Display / headline / title → serif (Baskerville), tight tracking like the design
    displayLarge =
      TextStyle(fontFamily = SerifFontFamily, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.5).sp),
    displayMedium =
      TextStyle(fontFamily = SerifFontFamily, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.25).sp),
    displaySmall =
      TextStyle(fontFamily = SerifFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge =
      TextStyle(fontFamily = SerifFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp),
    headlineMedium =
      TextStyle(fontFamily = SerifFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall =
      TextStyle(fontFamily = SerifFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge =
      TextStyle(fontFamily = SerifFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    // titleMedium / titleSmall stay sans — they label UI chrome, not content
    titleMedium =
      TextStyle(fontFamily = SansFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall =
      TextStyle(fontFamily = SansFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    // Body / label → sans (Inter)
    bodyLarge =
      TextStyle(fontFamily = SansFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium =
      TextStyle(fontFamily = SansFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    bodySmall =
      TextStyle(fontFamily = SansFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelLarge =
      TextStyle(fontFamily = SansFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium =
      TextStyle(fontFamily = SansFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall =
      TextStyle(fontFamily = SansFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
  )
