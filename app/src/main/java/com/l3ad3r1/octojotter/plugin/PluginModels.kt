package com.l3ad3r1.octojotter.plugin

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Community-plugin data contracts.
 *
 * A plugin is described by a [PluginManifest]. The community **registry** is a
 * single JSON index ([RegistryIndex]) hosted in the Octo-Jotter repo; each entry
 * points at a plugin's manifest. Phase 1 supports declarative `theme` plugins
 * only; `permissions` and the versioned `type` field leave room for the phase-2
 * scripting runtime without breaking older installs.
 */

@JsonClass(generateAdapter = true)
data class RegistryIndex(
    @Json(name = "plugins") val plugins: List<RegistryEntry> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RegistryEntry(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "author") val author: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "type") val type: String = "theme",
    @Json(name = "version") val version: String? = null,
    // Absolute URL (raw GitHub) of this plugin's manifest.json.
    @Json(name = "manifestUrl") val manifestUrl: String
)

@JsonClass(generateAdapter = true)
data class PluginManifest(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "version") val version: String,
    @Json(name = "minAppVersion") val minAppVersion: String? = null,
    @Json(name = "author") val author: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "type") val type: String = "theme",
    @Json(name = "permissions") val permissions: List<String> = emptyList(),
    // Present when type == "theme".
    @Json(name = "theme") val theme: ThemeSpec? = null
)

@JsonClass(generateAdapter = true)
data class ThemeSpec(
    @Json(name = "dark") val dark: Boolean = false,
    // Material color slot -> hex ("#RRGGBB" or "#AARRGGBB"). Unspecified slots
    // fall back to the built-in light/dark scheme.
    @Json(name = "colors") val colors: Map<String, String> = emptyMap()
)

object PluginTypes {
    const val THEME = "theme"
}
