package com.l3ad3r1.octojotter.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.l3ad3r1.octojotter.R

/**
 * The app-wide font choices offered in Settings → Appearance → Font. Three
 * are the system's own generic families (always present, zero download);
 * three are variable-weight TTFs bundled under res/font — Geist, Rubik and
 * IBM Plex Sans aren't preinstalled on Android, and a downloadable-fonts
 * provider needs Play Services certificates that would silently break font
 * loading if ever mistyped, so these ship in the APK instead.
 *
 * Applied to every Typography role uniformly (see Theme.kt) — this replaces
 * the app's whole voice, not just body text, so "Serif" and "Sans Serif"
 * read as real, distinct choices rather than one of them matching what was
 * already there.
 */
enum class AppFontOption(val id: String, val label: String, val fontFamily: FontFamily) {
    SANS_SERIF("sans_serif", "Sans Serif", FontFamily.SansSerif),
    SERIF("serif", "Serif", FontFamily.Serif),
    MONOSPACE("monospace", "Monospace", FontFamily.Monospace),
    GEIST("geist", "Geist", FontFamily(Font(R.font.geist_variable))),
    RUBIK("rubik", "Rubik", FontFamily(Font(R.font.rubik_variable))),
    IBM_PLEX_SANS("ibm_plex_sans", "IBM Plex Sans", FontFamily(Font(R.font.ibm_plex_sans_variable)));

    companion object {
        fun fromId(id: String): AppFontOption = entries.firstOrNull { it.id == id } ?: SANS_SERIF
    }
}
