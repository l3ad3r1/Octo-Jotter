package com.l3ad3r1.octojotter.plugin

/**
 * The bridge a plugin's sandboxed JS uses to affect the app. Implemented by the
 * app layer (ViewModel). Methods are invoked synchronously from the script
 * thread, so implementations must be quick / non-blocking-on-UI.
 *
 * Every capability here is gated by a permission the user granted at install
 * time; [ScriptEngine] performs the permission check before calling through.
 */
interface PluginHost {
    /** Create a new note. Requires [PluginPermissions.NOTES_WRITE]. */
    fun createNote(title: String, content: String)

    /** Titles of all existing notes. Requires [PluginPermissions.NOTES_READ]. */
    fun listNoteTitles(): List<String>

    /** Read-only note snapshots. Requires [PluginPermissions.NOTES_READ]. */
    fun listNotes(): List<PluginNote>

    /** Full-text search over readable notes. Requires [PluginPermissions.NOTES_READ]. */
    fun searchNotes(query: String): List<PluginNote>

    /** Notes carrying a tag. Requires [PluginPermissions.NOTES_READ]. */
    fun notesWithTag(tag: String): List<PluginNote>

    /** Open Markdown task-list items across readable notes. Requires [PluginPermissions.NOTES_READ]. */
    fun openTasks(): List<PluginTask>

    /** Diagnostic logging (no permission required). */
    fun log(pluginId: String, message: String)
}

data class PluginNote(
    val id: Int,
    val title: String,
    val displayTitle: String,
    val content: String,
    val tags: List<String>,
    val folder: String?,
    val path: String?,
    val lastModifiedLocally: Long,
    val locked: Boolean
)

data class PluginTask(
    val noteId: Int,
    val noteTitle: String,
    val text: String,
    val line: String,
    val lineNumber: Int,
    val tags: List<String>,
    val folder: String?,
    val lastModifiedLocally: Long
)
