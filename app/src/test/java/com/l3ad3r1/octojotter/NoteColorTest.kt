package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.data.local.NoteEntity
import com.l3ad3r1.octojotter.ui.NoteColor
import com.l3ad3r1.octojotter.ui.displayColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteColorTest {

    private fun note(
        content: String = "",
        color: String? = null,
        locked: Boolean = false,
        encrypted: Boolean = false,
        reminderAt: Long? = null,
        isDailyNote: Boolean = false,
    ) = NoteEntity(
        title = "Note",
        content = content,
        color = color,
        locked = locked,
        encrypted = encrypted,
        reminderAt = reminderAt,
        isDailyNote = isDailyNote,
    )

    @Test
    fun `a plain note with no manual color has none`() {
        assertNull(note().displayColor())
    }

    @Test
    fun `a plain note keeps its manually picked color`() {
        assertEquals(NoteColor.PINK, note(color = "pink").displayColor())
    }

    @Test
    fun `a locked note is purple regardless of a manual color`() {
        assertEquals(NoteColor.PURPLE, note(locked = true, color = "pink").displayColor())
    }

    @Test
    fun `an encrypted note is purple too`() {
        assertEquals(NoteColor.PURPLE, note(encrypted = true).displayColor())
    }

    @Test
    fun `a note with a reminder is yellow`() {
        assertEquals(NoteColor.YELLOW, note(reminderAt = 1_000L).displayColor())
    }

    @Test
    fun `the daily note is blue`() {
        assertEquals(NoteColor.BLUE, note(isDailyNote = true).displayColor())
    }

    @Test
    fun `a note with a checklist item is green`() {
        assertEquals(NoteColor.GREEN, note(content = "- [ ] buy milk").displayColor())
    }

    @Test
    fun `a checked checklist item still counts`() {
        assertEquals(NoteColor.GREEN, note(content = "prose\n- [x] done thing\nmore prose").displayColor())
    }

    @Test
    fun `an asterisk bullet checklist item counts too`() {
        assertEquals(NoteColor.GREEN, note(content = "* [ ] task").displayColor())
    }

    @Test
    fun `a bare dash list is not a checklist`() {
        assertNull(note(content = "- just a bullet, no checkbox").displayColor())
    }

    @Test
    fun `locked wins over reminder, daily note and checklist`() {
        val n = note(locked = true, reminderAt = 1L, isDailyNote = true, content = "- [ ] x")
        assertEquals(NoteColor.PURPLE, n.displayColor())
    }

    @Test
    fun `reminder wins over daily note and checklist`() {
        val n = note(reminderAt = 1L, isDailyNote = true, content = "- [ ] x")
        assertEquals(NoteColor.YELLOW, n.displayColor())
    }

    @Test
    fun `daily note wins over checklist`() {
        val n = note(isDailyNote = true, content = "- [ ] x")
        assertEquals(NoteColor.BLUE, n.displayColor())
    }
}
