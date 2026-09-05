package com.l3ad3r1.octojotter.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Octo Jotter — "Ink & Paper" palette
// Teal on white in light mode, teal on true black in dark mode. The teal is the
// launcher mark's own ink colour (#275B71, sampled from assets/icon-source.png),
// so the app opens into the same palette its icon and splash use.
//
// Dark mode is OLED-true-black: background and surface are #000000 and the
// elevation ramp starts just above it, so cards stay readable without lifting
// the whole canvas off black.
//
// Every onX/X pairing below is >= 4.5:1 (WCAG AA for body text).
// ---------------------------------------------------------------------------

// Brand ink — the colour the octopus is drawn in.
val OctoTeal = Color(0xFF275B71)

@Deprecated("Renamed to OctoTeal when the palette moved off indigo.", ReplaceWith("OctoTeal"))
val OctoAmber = OctoTeal

// Light primary group (brand teal)
val md_primary_light = Color(0xFF275B71) // 7.5:1 on white
val md_onPrimary_light = Color(0xFFFFFFFF)
val md_primaryContainer_light = Color(0xFFCDE7F0)
val md_onPrimaryContainer_light = Color(0xFF072A38)

// Light secondary group
val md_secondary_light = Color(0xFF4A6673)
val md_onSecondary_light = Color(0xFFFFFFFF)
val md_secondaryContainer_light = Color(0xFFDAE8EE)
val md_onSecondaryContainer_light = Color(0xFF142831)

// Light tertiary group
val md_tertiary_light = Color(0xFF0F7B94)
val md_onTertiary_light = Color(0xFFFFFFFF)
val md_tertiaryContainer_light = Color(0xFFC9EAF3)
val md_onTertiaryContainer_light = Color(0xFF052F3A)

// Error stays red — it's semantic, not brand.
val md_error_light = Color(0xFFDC2626)
val md_onError_light = Color(0xFFFFFFFF)
val md_errorContainer_light = Color(0xFFFEE2E2)
val md_onErrorContainer_light = Color(0xFF7F1D1D)

val md_background_light = Color(0xFFFFFFFF)
val md_onBackground_light = Color(0xFF0E1A1F)
val md_surface_light = Color(0xFFFFFFFF)
val md_onSurface_light = Color(0xFF0E1A1F)
val md_surfaceVariant_light = Color(0xFFE4EEF2)
val md_onSurfaceVariant_light = Color(0xFF3E545E)
val md_outline_light = Color(0xFF78929C)
val md_outlineVariant_light = Color(0xFFC5D7DE)

// Light surface container ramp — white upward into cool teal-greys
val md_surfaceContainerLowest_light = Color(0xFFFFFFFF)
val md_surfaceContainerLow_light = Color(0xFFF7FBFC)
val md_surfaceContainer_light = Color(0xFFEFF6F9)
val md_surfaceContainerHigh_light = Color(0xFFE4EEF2)
val md_surfaceContainerHighest_light = Color(0xFFD6E6EC)

val md_inverseSurface_light = Color(0xFF1A2A31)
val md_inverseOnSurface_light = Color(0xFFEDF5F8)
val md_inversePrimary_light = Color(0xFF85CBE2)
val md_scrim_light = Color(0xFF000000)

// Dark primary group (lifted teal — 10.8:1 on black)
val md_primary_dark = Color(0xFF6FC7E0)
val md_onPrimary_dark = Color(0xFF00323F)
val md_primaryContainer_dark = Color(0xFF1B4E60)
val md_onPrimaryContainer_dark = Color(0xFFC2E9F5)

// Dark secondary group
val md_secondary_dark = Color(0xFFA2BAC4)
val md_onSecondary_dark = Color(0xFF0A1B21)
val md_secondaryContainer_dark = Color(0xFF293C45)
val md_onSecondaryContainer_dark = Color(0xFFDCE9EF)

// Dark tertiary group
val md_tertiary_dark = Color(0xFF4FD3EA)
val md_onTertiary_dark = Color(0xFF00323C)
val md_tertiaryContainer_dark = Color(0xFF115B6C)
val md_onTertiaryContainer_dark = Color(0xFFCBEFF8)

val md_error_dark = Color(0xFFF87171)
val md_onError_dark = Color(0xFF450A0A)
val md_errorContainer_dark = Color(0xFF991B1B)
val md_onErrorContainer_dark = Color(0xFFFEE2E2)

val md_background_dark = Color(0xFF000000) // true black
val md_onBackground_dark = Color(0xFFE7F1F5)
val md_surface_dark = Color(0xFF000000)
val md_onSurface_dark = Color(0xFFE7F1F5)
val md_surfaceVariant_dark = Color(0xFF1D292F)
val md_onSurfaceVariant_dark = Color(0xFFAAC0C9)
val md_outline_dark = Color(0xFF5B767F)
val md_outlineVariant_dark = Color(0xFF2B3A41)

// Dark surface container ramp — starts at black, lifts just enough to separate cards
val md_surfaceContainerLowest_dark = Color(0xFF000000)
val md_surfaceContainerLow_dark = Color(0xFF05090B)
val md_surfaceContainer_dark = Color(0xFF0B1317)
val md_surfaceContainerHigh_dark = Color(0xFF111B20)
val md_surfaceContainerHighest_dark = Color(0xFF182529)

val md_inverseSurface_dark = Color(0xFFE7F1F5)
val md_inverseOnSurface_dark = Color(0xFF10191D)
val md_inversePrimary_dark = Color(0xFF275B71)
val md_scrim_dark = Color(0xFF000000)
