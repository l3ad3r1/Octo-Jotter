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

    /** Diagnostic logging (no permission required). */
    fun log(pluginId: String, message: String)
}
