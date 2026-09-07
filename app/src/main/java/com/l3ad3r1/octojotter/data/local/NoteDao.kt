package com.l3ad3r1.octojotter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY pinned DESC, lastModifiedLocally DESC")
    fun getAllNotesFlow(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashNotesFlow(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Int): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteByIdFlow(id: Int): Flow<NoteEntity?>

    // Gist-only dirty notes (repository IS NULL) so repo notes are never
    // accidentally pushed as new Gists by the Gist sync path.
    @Query("SELECT * FROM notes WHERE needsSync = 1 AND repository IS NULL AND deletedAt IS NULL")
    suspend fun getNotesToSync(): List<NoteEntity>

    // Dirty notes belonging to a specific repository.
    @Query("SELECT * FROM notes WHERE needsSync = 1 AND repository = :repository AND deletedAt IS NULL")
    suspend fun getNotesToSyncForRepository(repository: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE repository = :repository AND path = :path LIMIT 1")
    suspend fun getNoteByRepoAndPath(repository: String, path: String): NoteEntity?

    // `ESCAPE '\'` so a literal % or _ typed into the search box matches
    // itself instead of acting as a wildcard (see NoteRepository.escapeLike).
    // A locked note matches on its title only: its body is meant to be hidden,
    // and matching on content leaked it back through the result list.
    @Query("""
        SELECT * FROM notes
        WHERE deletedAt IS NULL
          AND (title LIKE :query ESCAPE '\' OR (locked = 0 AND content LIKE :query ESCAPE '\'))
        ORDER BY pinned DESC, lastModifiedLocally DESC
    """)
    fun searchNotesFlow(query: String): Flow<List<NoteEntity>>

    @Query("""
        SELECT * FROM notes
        WHERE deletedAt IS NULL
          AND (title LIKE :searchPattern ESCAPE '\' OR (locked = 0 AND content LIKE :searchPattern ESCAPE '\'))
        ORDER BY
            pinned DESC,
            CASE WHEN :sortBy = 'TITLE' THEN title END ASC,
            CASE WHEN :sortBy = 'LAST_MODIFIED' THEN lastModifiedLocally END DESC
    """)
    fun getNotesFilteredAndSorted(searchPattern: String, sortBy: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    /**
     * Write just the text the editor owns.
     *
     * The editor holds a snapshot of the whole row, and autosave used to write
     * that snapshot back with `@Update`. A sync completing mid-edit — which
     * every autosave triggers — would then be undone: the freshly-assigned
     * `gistId`/`sha` reverted to the snapshot's nulls, and the next push
     * created a *second* Gist for the same note. Touching only the four columns
     * the editor actually changes makes that race impossible.
     */
    @Query("UPDATE notes SET title = :title, content = :content, lastModifiedLocally = :modifiedAt, needsSync = 1 WHERE id = :id")
    suspend fun updateNoteText(id: Int, title: String, content: String, modifiedAt: Long)

    // --- Sync writes -------------------------------------------------------
    // Sync reads a note, talks to GitHub for a while, then writes back. Doing
    // that with a whole-row `@Update` meant an edit made during the round trip
    // was silently reverted. These write only the columns sync owns, and the
    // "this note is now clean" half is guarded on the note not having been
    // edited since it was read — if it was, needsSync stays set and the next
    // pass pushes the newer text.

    /** Remember which remote object a note maps to. Never conditional: losing
     *  this is what creates duplicate Gists on the next push. */
    @Query("UPDATE notes SET gistId = :gistId, remoteFilename = :remoteFilename WHERE id = :id")
    suspend fun setGistIdentity(id: Int, gistId: String?, remoteFilename: String?)

    @Query("UPDATE notes SET path = :path, sha = :sha WHERE id = :id")
    suspend fun setRepoIdentity(id: Int, path: String?, sha: String?)

    @Query("""
        UPDATE notes SET
            needsSync = 0,
            remoteUpdatedAt = :remoteUpdatedAt,
            lastSyncedContentHash = :contentHash,
            conflictState = NULL,
            conflictedRemoteContent = NULL,
            conflictedRemoteModifiedAt = NULL
        WHERE id = :id AND lastModifiedLocally = :unchangedSince
    """)
    suspend fun markSynced(id: Int, remoteUpdatedAt: String?, contentHash: String, unchangedSince: Long)

    @Query("""
        UPDATE notes SET
            sha = :sha,
            conflictState = 'CONFLICT',
            conflictedRemoteContent = :remoteContent,
            conflictedRemoteModifiedAt = :remoteModifiedAt,
            needsSync = 0
        WHERE id = :id
    """)
    suspend fun markConflict(id: Int, sha: String?, remoteContent: String, remoteModifiedAt: Long)

    /**
     * Take the remote copy for a note with no pending local edits. The
     * `needsSync = 0` guard makes this a no-op if the user started typing
     * between the pull deciding to accept the remote and this write landing.
     * Returns the number of rows changed, so the caller knows whether to
     * re-extract tags.
     */
    @Query("""
        UPDATE notes SET
            title = :title,
            content = :content,
            sha = :sha,
            remoteFilename = :remoteFilename,
            remoteUpdatedAt = :remoteUpdatedAt,
            lastSyncedContentHash = :contentHash,
            lastModifiedLocally = :modifiedAt,
            needsSync = 0,
            conflictState = NULL,
            conflictedRemoteContent = NULL,
            conflictedRemoteModifiedAt = NULL
        WHERE id = :id AND needsSync = 0
    """)
    suspend fun applyRemoteContent(
        id: Int,
        title: String,
        content: String,
        sha: String?,
        remoteFilename: String?,
        remoteUpdatedAt: String?,
        contentHash: String,
        modifiedAt: Long,
    ): Int

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM notes WHERE gistId = :gistId LIMIT 1")
    suspend fun getNoteByGistId(gistId: String): NoteEntity?

    @Query("SELECT * FROM drafts WHERE noteId = :noteId LIMIT 1")
    suspend fun getDraftByNoteId(noteId: Int): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE noteId = :noteId")
    suspend fun deleteDraftByNoteId(noteId: Int)

    @Query("SELECT * FROM notes")
    suspend fun getAllNotes(): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NOT NULL")
    fun getTrashCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE needsSync = 1 AND deletedAt IS NULL")
    fun getPendingSyncCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE conflictState = 'CONFLICT' AND deletedAt IS NULL")
    fun getConflictCountFlow(): Flow<Int>

    @Query("SELECT * FROM notes WHERE conflictState = 'CONFLICT' AND deletedAt IS NULL ORDER BY lastModifiedLocally DESC")
    fun getConflictedNotesFlow(): Flow<List<NoteEntity>>

    // Trashing is local and reversible: it never queues the remote copy for
    // deletion. Emptying the trash is what does that (see queueTrashForPurge).
    @Query("UPDATE notes SET deletedAt = :deletedAt, pendingRemoteDelete = 0, needsSync = 0 WHERE id = :id")
    suspend fun moveToTrash(id: Int, deletedAt: Long)

    @Query("UPDATE notes SET deletedAt = NULL, pendingRemoteDelete = 0, needsSync = 1 WHERE id = :id")
    suspend fun restoreFromTrash(id: Int)

    /**
     * Mark every trashed note for permanent deletion. Rows with a remote copy
     * keep their tombstone until [NoteRepository.processPendingRemoteDeletes]
     * confirms the Gist/repo file is gone; purely local ones are removed by
     * [purgeDeletableTrash] straight away.
     */
    @Query("UPDATE notes SET pendingRemoteDelete = 1 WHERE deletedAt IS NOT NULL")
    suspend fun queueTrashForPurge()

    /** Trashed notes still waiting for their remote copy to be deleted. */
    @Query("SELECT * FROM notes WHERE pendingRemoteDelete = 1 AND deletedAt IS NOT NULL")
    suspend fun getNotesPendingRemoteDelete(): List<NoteEntity>

    /**
     * Drop trashed rows that have nothing left on GitHub — either they never
     * had a remote copy, or it has since been deleted (which clears
     * `pendingRemoteDelete`). Anything still queued survives so the next sync
     * can retry, which is what stops an offline "Empty trash" from resurrecting
     * the note on the following pull.
     */
    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND pendingRemoteDelete = 0")
    suspend fun purgeDeletableTrash()

    /** How many emptied notes are still waiting on a remote delete. */
    @Query("SELECT COUNT(*) FROM notes WHERE pendingRemoteDelete = 1 AND deletedAt IS NOT NULL")
    suspend fun countPendingRemoteDeletes(): Int

    @Query("UPDATE notes SET locked = :locked WHERE id = :id")
    suspend fun setLocked(id: Int, locked: Boolean)

    @Query("SELECT * FROM drafts")
    suspend fun getAllDrafts(): List<DraftEntity>

    @Query("""
        SELECT notes.* FROM notes
        INNER JOIN note_tag_cross_ref ON notes.id = note_tag_cross_ref.noteId
        WHERE note_tag_cross_ref.tagName = :tagName AND notes.deletedAt IS NULL
        ORDER BY notes.pinned DESC, notes.lastModifiedLocally DESC
    """)
    fun getNotesByTag(tagName: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteTagCrossRef(crossRef: NoteTagCrossRef)

    @Query("DELETE FROM note_tag_cross_ref WHERE noteId = :noteId")
    suspend fun deleteNoteTagCrossRefs(noteId: Int)

    // Not a plain "SELECT * FROM tags": updateTagsForNote only ever removes a
    // note's own cross-refs and re-adds the current ones, it never deletes the
    // tags row itself — so a tag that no note references any more (a typo
    // fixed, a false-positive #21 that turned out to be a PR number) would
    // otherwise sit in this list forever. Requiring a live cross-ref makes the
    // tag list self-cleaning instead of needing an explicit prune step.
    @Query("""
        SELECT DISTINCT tags.* FROM tags
        INNER JOIN note_tag_cross_ref ON tags.name = note_tag_cross_ref.tagName
    """)
    fun getAllTagsFlow(): Flow<List<TagEntity>>

    // targetTitle arrives LIKE-escaped (NoteRepository.escapeLike) so a title
    // containing _ or % links back to itself, not to every similar note.
    @Query("""
        SELECT * FROM notes
        WHERE deletedAt IS NULL
          AND content LIKE '%[[' || :targetTitle || ']]%' ESCAPE '\'
          AND id != :currentNoteId
    """)
    fun getBacklinks(targetTitle: String, currentNoteId: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE title = :title AND deletedAt IS NULL LIMIT 1")
    suspend fun getNoteByTitle(title: String): NoteEntity?

    @Query("UPDATE notes SET color = :color WHERE id = :id")
    suspend fun setColor(id: Int, color: String?)

    @Query("UPDATE notes SET reminderAt = :reminderAt WHERE id = :id")
    suspend fun setReminderAt(id: Int, reminderAt: Long?)

    @Query("SELECT * FROM notes WHERE isDailyNote = 1 AND title = :title AND deletedAt IS NULL LIMIT 1")
    suspend fun getDailyNoteByTitle(title: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL AND deletedAt IS NULL")
    suspend fun getNotesWithReminders(): List<NoteEntity>

    @Transaction
    suspend fun updateTagsForNote(noteId: Int, tags: List<String>) {
        deleteNoteTagCrossRefs(noteId)
        for (tagName in tags) {
            insertTag(TagEntity(tagName))
            insertNoteTagCrossRef(NoteTagCrossRef(noteId, tagName))
        }
    }
}
