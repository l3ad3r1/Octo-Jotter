package com.l3ad3r1.octojotter.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.l3ad3r1.octojotter.plugin.ThemeSpec

/** A theme-plugin's resolved appearance, ready to hand to MyApplicationTheme. */
data class PluginThemeState(val dark: Boolean, val colorScheme: ColorScheme)

/**
 * Build a Material [ColorScheme] from a plugin [ThemeSpec]. Unspecified slots
 * fall back to the built-in light/dark scheme, so a plugin can override just a
 * few colors without having to define the whole palette.
 */
fun buildPluginColorScheme(spec: ThemeSpec): ColorScheme {
    val base = if (spec.dark) darkColorScheme() else lightColorScheme()
    fun c(key: String, default: Color): Color = spec.colors[key]?.let { parseHexColor(it) } ?: default
    return base.copy(
        primary = c("primary", base.primary),
        onPrimary = c("onPrimary", base.onPrimary),
        primaryContainer = c("primaryContainer", base.primaryContainer),
        onPrimaryContainer = c("onPrimaryContainer", base.onPrimaryContainer),
        secondary = c("secondary", base.secondary),
        onSecondary = c("onSecondary", base.onSecondary),
        secondaryContainer = c("secondaryContainer", base.secondaryContainer),
        onSecondaryContainer = c("onSecondaryContainer", base.onSecondaryContainer),
        tertiary = c("tertiary", base.tertiary),
        onTertiary = c("onTertiary", base.onTertiary),
        tertiaryContainer = c("tertiaryContainer", base.tertiaryContainer),
        onTertiaryContainer = c("onTertiaryContainer", base.onTertiaryContainer),
        background = c("background", base.background),
        onBackground = c("onBackground", base.onBackground),
        surface = c("surface", base.surface),
        onSurface = c("onSurface", base.onSurface),
        surfaceVariant = c("surfaceVariant", base.surfaceVariant),
        onSurfaceVariant = c("onSurfaceVariant", base.onSurfaceVariant),
        surfaceContainer = c("surfaceContainer", base.surfaceContainer),
        error = c("error", base.error),
        onError = c("onError", base.onError),
        errorContainer = c("errorContainer", base.errorContainer),
        onErrorContainer = c("onErrorContainer", base.onErrorContainer),
        outline = c("outline", base.outline),
        outlineVariant = c("outlineVariant", base.outlineVariant),
    )
}

private fun parseHexColor(hex: String): Color? = try {
    Color(android.graphics.Color.parseColor(hex.trim()))
} catch (e: Exception) {
    null
}
