package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.data.local.NoteEntity
import com.l3ad3r1.octojotter.data.repository.PurgeDecision
import com.l3ad3r1.octojotter.data.repository.decidePurge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * What permanently deleting a trashed note requires.
 *
 * This decides whether a local row may be dropped outright. Getting it wrong is
 * how deleted notes used to come back: `pendingRemoteDelete` was written but
 * never read by anything, emptying the Trash removed rows locally only, and the
 * next pull found the Gist still on GitHub and re-inserted the note. Only
 * [PurgeDecision.DropRow] may skip the remote delete.
 */
class PurgeDecisionTest {

    private fun note(
        gistId: String? = null,
        repository: String? = null,
        path: String? = null,
    ) = NoteEntity(
        id = 1,
        title = "note",
        content = "body",
        gistId = gistId,
        repository = repository,
        path = path,
    )

    @Test
    fun `a purely local note can be dropped outright`() {
        assertEquals(PurgeDecision.DropRow, decidePurge(note()))
    }

    @Test
    fun `a gist-backed note must have its gist deleted first`() {
        assertEquals(PurgeDecision.DeleteGist, decidePurge(note(gistId = "abc123")))
    }

    @Test
    fun `a repo-backed note must have its file deleted first`() {
        val n = note(repository = "l3ad3r1/vault", path = "Notes/idea.md")
        assertEquals(PurgeDecision.DeleteRepoFile, decidePurge(n))
    }

    @Test
    fun `a synced note is never droppable`() {
        // The regression guard. Either of these resolving to DropRow means the
        // row goes while the remote copy stays, and the note reappears.
        assertNotEquals(PurgeDecision.DropRow, decidePurge(note(gistId = "abc123")))
        assertNotEquals(
            PurgeDecision.DropRow,
            decidePurge(note(repository = "l3ad3r1/vault", path = "Notes/idea.md")),
        )
    }

    @Test
    fun `a repo-backed note that also has a gist id deletes the repo file`() {
        // Can happen to a note that started life as a Gist and was later moved
        // into a repository: the repo file is the live copy.
        val n = note(gistId = "stale", repository = "l3ad3r1/vault", path = "Notes/idea.md")
        assertEquals(PurgeDecision.DeleteRepoFile, decidePurge(n))
    }

    @Test
    fun `blank identifiers count as absent rather than as a remote copy`() {
        // Empty strings must not be mistaken for a real Gist id, or the purge
        // would wait forever on a delete that can never succeed.
        assertEquals(PurgeDecision.DropRow, decidePurge(note(gistId = "", repository = "")))
    }
}
