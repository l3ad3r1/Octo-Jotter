package com.l3ad3r1.octojotter.data.local

import androidx.room.Dao
import androidx.room.Delete
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

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND (title LIKE :query OR content LIKE :query) ORDER BY pinned DESC, lastModifiedLocally DESC")
    fun searchNotesFlow(query: String): Flow<List<NoteEntity>>

    @Query("""
        SELECT * FROM notes 
        WHERE deletedAt IS NULL AND (title LIKE :searchPattern OR content LIKE :searchPattern)
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

    @Delete
    suspend fun delete(note: NoteEntity)

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

    @Query("UPDATE notes SET deletedAt = :deletedAt, pendingRemoteDelete = :pendingRemoteDelete, needsSync = 0 WHERE id = :id")
    suspend fun moveToTrash(id: Int, deletedAt: Long, pendingRemoteDelete: Boolean)

    @Query("UPDATE notes SET deletedAt = NULL, pendingRemoteDelete = 0, needsSync = 1 WHERE id = :id")
    suspend fun restoreFromTrash(id: Int)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("UPDATE notes SET locked = :locked WHERE id = :id")
    suspend fun setLocked(id: Int, locked: Boolean)

    @Query("SELECT * FROM drafts")
    suspend fun getAllDrafts(): List<DraftEntity>

    @Transaction
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY pinned DESC, lastModifiedLocally DESC")
    fun getNotesWithTags(): Flow<List<NoteWithTags>>

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

    @Query("SELECT * FROM tags")
    fun getAllTagsFlow(): Flow<List<TagEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND content LIKE '%[[' || :targetTitle || ']]%' AND id != :currentNoteId")
    fun getBacklinks(targetTitle: String, currentNoteId: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE title = :title AND deletedAt IS NULL LIMIT 1")
    suspend fun getNoteByTitle(title: String): NoteEntity?

    @Transaction
    suspend fun updateTagsForNote(noteId: Int, tags: List<String>) {
        deleteNoteTagCrossRefs(noteId)
        for (tagName in tags) {
            insertTag(TagEntity(tagName))
            insertNoteTagCrossRef(NoteTagCrossRef(noteId, tagName))
        }
    }
}
