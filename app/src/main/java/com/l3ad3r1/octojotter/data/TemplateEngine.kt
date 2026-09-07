package com.l3ad3r1.octojotter.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Expands the variables a Templates-plugin template may contain, the same
 * small vocabulary Obsidian's own templates use: `{{date}}`, `{{time}}`,
 * `{{title}}`. `{{cursor}}` is accepted too but simply removed — this app's
 * editor doesn't yet support seeding the initial caret position from a note's
 * starting content, so it's a no-op placeholder rather than a broken promise.
 */
object TemplateEngine {
    // SimpleDateFormat is not thread-safe and these are called from coroutines
    // (template expansion, Daily Notes, plugin commands), so each call gets its
    // own — a shared instance can interleave into a garbled date or throw.
    // Locale.getDefault() is read per call too, so a locale change mid-session
    // is picked up.
    private fun dateFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private fun timeFormat() = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun render(templateContent: String, title: String, now: Date = Date()): String =
        templateContent
            .replace("{{date}}", dateFormat().format(now))
            .replace("{{time}}", timeFormat().format(now))
            .replace("{{title}}", title)
            .replace("{{cursor}}", "")

    /** Today's date formatted the way Daily Notes titles use, e.g. "2026-09-07". */
    fun todayTitle(now: Date = Date()): String = dateFormat().format(now)
}
