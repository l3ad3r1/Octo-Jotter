package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.data.repository.gistFilesForPush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The `files` map a Gist push sends.
 *
 * Renaming a note used to key the request by the *new* filename, which the
 * Gist API treats as "add a file" rather than "rename this file". The old file
 * stayed behind, and because a pull takes the first `.md` file it finds, the
 * next sync could restore the note's pre-rename title and body. These tests pin
 * the request shape that makes a rename a rename.
 */
class GistPushShapeTest {

    @Test
    fun `an unchanged title is keyed by its own name with no rename`() {
        val files = gistFilesForPush("Meeting notes.md", "Meeting notes.md", "body")
        assertEquals(setOf("Meeting notes.md"), files.keys)
        assertEquals("body", files.getValue("Meeting notes.md").content)
        // No `filename` field: nothing is being renamed.
        assertNull(files.getValue("Meeting notes.md").filename)
    }

    @Test
    fun `a rename is keyed by the old name and carries the new one`() {
        val files = gistFilesForPush("Old title.md", "New title.md", "body")
        // Keyed by the OLD name — this is the whole fix. Keying by the new name
        // leaves "Old title.md" on the Gist as a second file.
        assertEquals(setOf("Old title.md"), files.keys)
        assertEquals("New title.md", files.getValue("Old title.md").filename)
        assertEquals("body", files.getValue("Old title.md").content)
    }

    @Test
    fun `a rename never emits the new filename as a key`() {
        // Regression guard for the exact bug: two entries, or an entry keyed by
        // the new name, both leave an orphan file behind.
        val files = gistFilesForPush("Old title.md", "New title.md", "body")
        assertEquals(1, files.size)
        assert(!files.containsKey("New title.md")) {
            "keying by the new filename adds a second file instead of renaming"
        }
    }

    @Test
    fun `a note never pushed before is keyed by its new name`() {
        // remoteFilename is null until the first successful push; there is no
        // old name to rename from.
        val files = gistFilesForPush(null, "Fresh note.md", "body")
        assertEquals(setOf("Fresh note.md"), files.keys)
        assertNull(files.getValue("Fresh note.md").filename)
    }

    @Test
    fun `a title differing only in case is still treated as a rename`() {
        val files = gistFilesForPush("todo.md", "Todo.md", "body")
        assertEquals(setOf("todo.md"), files.keys)
        assertEquals("Todo.md", files.getValue("todo.md").filename)
    }
}
