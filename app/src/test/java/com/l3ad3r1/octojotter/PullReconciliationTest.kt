package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.data.local.NoteEntity
import com.l3ad3r1.octojotter.data.repository.PullDecision
import com.l3ad3r1.octojotter.data.repository.decidePull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reconciliation rule a repository pull applies to each remote file.
 *
 * This is the decision that can destroy a user's writing: choose wrong and a
 * pull silently overwrites edits that were never pushed. Every branch is
 * covered here, including the ordering between them.
 */
class PullReconciliationTest {

    private fun note(
        content: String,
        needsSync: Boolean,
        path: String = "Notes/idea.md",
    ) = NoteEntity(
        id = 1,
        title = "idea",
        content = content,
        needsSync = needsSync,
        repository = "l3ad3r1/vault",
        path = path,
    )

    @Test
    fun `a file with no local row is taken as a new note`() {
        assertEquals(PullDecision.Insert, decidePull(null, "# From the repo"))
    }

    @Test
    fun `a clean local note accepts the remote copy`() {
        val local = note("stale text", needsSync = false)
        assertEquals(PullDecision.AcceptRemote, decidePull(local, "fresher text"))
    }

    @Test
    fun `edits on both sides raise a conflict rather than picking a winner`() {
        val local = note("my unsynced draft", needsSync = true)
        assertEquals(PullDecision.Conflict, decidePull(local, "someone else's version"))
    }

    @Test
    fun `a local edit identical to the remote is left alone`() {
        // Same text on both sides: nothing to merge, and the row must keep its
        // needsSync flag so the push side is the one that clears it.
        val local = note("same on both sides", needsSync = true)
        assertEquals(PullDecision.Skip, decidePull(local, "same on both sides"))
    }

    @Test
    fun `conflict is checked before the clean-note case`() {
        // Ordering regression guard. If AcceptRemote were evaluated first, a
        // dirty note would be overwritten and the local edit lost silently.
        val dirty = note("unsynced work", needsSync = true)
        val decision = decidePull(dirty, "remote text")
        assertEquals(PullDecision.Conflict, decision)
        assert(decision != PullDecision.AcceptRemote)
    }

    @Test
    fun `whitespace-only differences still count as a conflict`() {
        // Content comparison is exact; trailing-newline churn from another
        // editor must not be silently resolved in the remote's favour.
        val local = note("line one", needsSync = true)
        assertEquals(PullDecision.Conflict, decidePull(local, "line one\n"))
    }

    @Test
    fun `an emptied remote file conflicts with pending local edits`() {
        // The dangerous shape: remote truncated to nothing while the user has
        // unsynced text. Must not resolve to AcceptRemote.
        val local = note("a paragraph the user wrote", needsSync = true)
        assertEquals(PullDecision.Conflict, decidePull(local, ""))
    }

    @Test
    fun `an emptied remote file overwrites a clean local note`() {
        // Same input, clean note: deleting the body upstream is a legitimate
        // edit to accept when nothing local is pending.
        val local = note("a paragraph", needsSync = false)
        assertEquals(PullDecision.AcceptRemote, decidePull(local, ""))
    }

    @Test
    fun `a blank local note with pending edits still conflicts`() {
        val local = note("", needsSync = true)
        assertEquals(PullDecision.Conflict, decidePull(local, "remote content"))
    }
}
