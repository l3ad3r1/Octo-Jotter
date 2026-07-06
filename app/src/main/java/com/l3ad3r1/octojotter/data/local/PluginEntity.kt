package com.l3ad3r1.octojotter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An installed community plugin. [payloadJson] caches the full manifest JSON so
 * the plugin works offline; type-specific handlers (e.g. the theme applier)
 * parse it on demand.
 */
@Entity(tableName = "plugins")
data class PluginEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val type: String,
    val author: String? = null,
    val description: String? = null,
    val enabled: Boolean = false,
    val sourceUrl: String? = null,
    val payloadJson: String,
    val permissions: List<String> = emptyList(),
    val installedAt: Long = System.currentTimeMillis()
)
