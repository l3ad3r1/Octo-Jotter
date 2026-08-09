package com.l3ad3r1.octojotter.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Undo/redo for the Markdown editor.
 *
 * Typing is coalesced: consecutive keystrokes within [COALESCE_WINDOW_MS] extend
 * the current entry instead of pushing a new one, so undo steps back by phrases
 * rather than by characters. Toolbar actions call [pushImmediate] so a format
 * always gets its own undo step.
 */
class EditorHistory(initial: TextFieldValue = TextFieldValue("")) {

    private data class Entry(val text: String, val selection: TextRange)

    private val entries = mutableListOf(Entry(initial.text, initial.selection))
    private var index = 0
    private var lastPushAt = 0L

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** Record an edit made by typing; coalesced with the previous one when it's recent. */
    fun record(value: TextFieldValue, now: Long = System.currentTimeMillis()) {
        val current = entries[index]
        if (current.text == value.text) {
            entries[index] = current.copy(selection = value.selection)
            return
        }
        if (now - lastPushAt < COALESCE_WINDOW_MS && index > 0) {
            entries[index] = Entry(value.text, value.selection)
        } else {
            push(value)
        }
        lastPushAt = now
        refreshFlags()
    }

    /** Record a discrete edit (a toolbar action) that should always be its own undo step. */
    fun pushImmediate(value: TextFieldValue, now: Long = System.currentTimeMillis()) {
        if (entries[index].text == value.text) return
        push(value)
        lastPushAt = now
        refreshFlags()
    }

    /** Reset the stack — used when a different note is loaded into the editor. */
    fun reset(value: TextFieldValue) {
        entries.clear()
        entries += Entry(value.text, value.selection)
        index = 0
        lastPushAt = 0L
        refreshFlags()
    }

    fun undo(): TextFieldValue? {
        if (!canUndo) return null
        index--
        refreshFlags()
        return entries[index].toValue()
    }

    fun redo(): TextFieldValue? {
        if (!canRedo) return null
        index++
        refreshFlags()
        return entries[index].toValue()
    }

    private fun push(value: TextFieldValue) {
        // Drop the redo branch, then append.
        while (entries.lastIndex > index) entries.removeAt(entries.lastIndex)
        entries += Entry(value.text, value.selection)
        if (entries.size > MAX_ENTRIES) entries.removeAt(0)
        index = entries.lastIndex
    }

    private fun refreshFlags() {
        canUndo = index > 0
        canRedo = index < entries.lastIndex
    }

    private fun Entry.toValue() = TextFieldValue(
        text = text,
        selection = TextRange(selection.start.coerceIn(0, text.length), selection.end.coerceIn(0, text.length))
    )

    companion object {
        const val COALESCE_WINDOW_MS = 700L
        const val MAX_ENTRIES = 100
    }
}
