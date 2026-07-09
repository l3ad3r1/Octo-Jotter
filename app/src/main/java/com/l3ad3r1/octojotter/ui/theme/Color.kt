package com.l3ad3r1.octojotter.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Octo Jotter — "Inkwell" palette
// Warm cream-paper design: amber ink accent on aged-paper cream, with soft
// ink-brown text. Ported from the Inkwell Figma design (src/index.css @theme):
//   cream #F5F0E8 / cream-dark #EBE5D8 / ink #1C1814 / ink-light #4A443C /
//   ink-faint #8C8278 / amber #8B6E3C / amber-light #B8924F / border #CECABF.
// Tonal values follow Material 3 conventions so light/dark stay balanced and
// keep AA contrast on their paired "on" roles.
// ---------------------------------------------------------------------------

// Primary — amber ink accent
val OctoAmber = Color(0xFF8B6E3C)

// Light primary group (amber)
val md_primary_light = Color(0xFF8B6E3C)
val md_onPrimary_light = Color(0xFFFBF7EF)
val md_primaryContainer_light = Color(0xFFECDCC0)
val md_onPrimaryContainer_light = Color(0xFF3A2A0E)

// Light secondary group (warm taupe)
val md_secondary_light = Color(0xFF6E6355)
val md_onSecondary_light = Color(0xFFFBF7EF)
val md_secondaryContainer_light = Color(0xFFE4DAC9)
val md_onSecondaryContainer_light = Color(0xFF241C10)

// Light tertiary group (muted sage — the "saved" green)
val md_tertiary_light = Color(0xFF5E7A62)
val md_onTertiary_light = Color(0xFFFFFFFF)
val md_tertiaryContainer_light = Color(0xFFD6E4D6)
val md_onTertiaryContainer_light = Color(0xFF1B2A1D)

val md_error_light = Color(0xFFBA1A1A)
val md_onError_light = Color(0xFFFFFFFF)
val md_errorContainer_light = Color(0xFFFFDAD6)
val md_onErrorContainer_light = Color(0xFF410002)

val md_background_light = Color(0xFFF5F0E8) // cream
val md_onBackground_light = Color(0xFF1C1814) // ink
val md_surface_light = Color(0xFFFEFCF9) // paper
val md_onSurface_light = Color(0xFF1C1814)
val md_surfaceVariant_light = Color(0xFFEBE5D8) // cream-dark
val md_onSurfaceVariant_light = Color(0xFF4A443C) // ink-light
val md_outline_light = Color(0xFF8C8278) // ink-faint
val md_outlineVariant_light = Color(0xFFD4CDBE)

// Light surface container ramp (paper → cream shades)
val md_surfaceContainerLowest_light = Color(0xFFFFFFFF)
val md_surfaceContainerLow_light = Color(0xFFFAF6EF)
val md_surfaceContainer_light = Color(0xFFF0EBE2) // toolbar tone
val md_surfaceContainerHigh_light = Color(0xFFEBE5D8)
val md_surfaceContainerHighest_light = Color(0xFFE3DCCD)

val md_inverseSurface_light = Color(0xFF2E2820)
val md_inverseOnSurface_light = Color(0xFFF0EBE2)
val md_inversePrimary_light = Color(0xFFE7C68C)
val md_scrim_light = Color(0xFF1C1814)

// Dark primary group (lighter amber, from the dark nav-drawer active state)
val md_primary_dark = Color(0xFFD4AB6E)
val md_onPrimary_dark = Color(0xFF3A2A0E)
val md_primaryContainer_dark = Color(0xFF5A4525)
val md_onPrimaryContainer_dark = Color(0xFFF0DCBE)

// Dark secondary group
val md_secondary_dark = Color(0xFFCFC4B2)
val md_onSecondary_dark = Color(0xFF33291A)
val md_secondaryContainer_dark = Color(0xFF4A4033)
val md_onSecondaryContainer_dark = Color(0xFFECE0CE)

// Dark tertiary group (sage)
val md_tertiary_dark = Color(0xFFA5C0A2)
val md_onTertiary_dark = Color(0xFF12281A)
val md_tertiaryContainer_dark = Color(0xFF2C4330)
val md_onTertiaryContainer_dark = Color(0xFFC0E0BE)

val md_error_dark = Color(0xFFFFB4AB)
val md_onError_dark = Color(0xFF690005)
val md_errorContainer_dark = Color(0xFF93000A)
val md_onErrorContainer_dark = Color(0xFFFFDAD6)

val md_background_dark = Color(0xFF1C1814) // ink
val md_onBackground_dark = Color(0xFFECE5D8) // cream
val md_surface_dark = Color(0xFF201C17)
val md_onSurface_dark = Color(0xFFECE5D8)
val md_surfaceVariant_dark = Color(0xFF4A443C)
val md_onSurfaceVariant_dark = Color(0xFFCDC5B6)
val md_outline_dark = Color(0xFF8C8278)
val md_outlineVariant_dark = Color(0xFF4A443C)

// Dark surface container ramp
val md_surfaceContainerLowest_dark = Color(0xFF141110)
val md_surfaceContainerLow_dark = Color(0xFF1C1814)
val md_surfaceContainer_dark = Color(0xFF211D18)
val md_surfaceContainerHigh_dark = Color(0xFF2C2721)
val md_surfaceContainerHighest_dark = Color(0xFF37312A)

val md_inverseSurface_dark = Color(0xFFECE5D8)
val md_inverseOnSurface_dark = Color(0xFF34302A)
val md_inversePrimary_dark = Color(0xFF8B6E3C)
val md_scrim_dark = Color(0xFF000000)
