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
    @Query("SELECT * FROM notes ORDER BY pinned DESC, lastModifiedLocally DESC")
    fun getAllNotesFlow(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Int): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteByIdFlow(id: Int): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE needsSync = 1")
    suspend fun getNotesToSync(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE title LIKE :query OR content LIKE :query ORDER BY pinned DESC, lastModifiedLocally DESC")
    fun searchNotesFlow(query: String): Flow<List<NoteEntity>>

    @Query("""
        SELECT * FROM notes 
        WHERE (title LIKE :searchPattern OR content LIKE :searchPattern)
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

    @Query("SELECT * FROM drafts")
    suspend fun getAllDrafts(): List<DraftEntity>

    @Transaction
    @Query("SELECT * FROM notes ORDER BY pinned DESC, lastModifiedLocally DESC")
    fun getNotesWithTags(): Flow<List<NoteWithTags>>

    @Query("""
        SELECT notes.* FROM notes
        INNER JOIN note_tag_cross_ref ON notes.id = note_tag_cross_ref.noteId
        WHERE note_tag_cross_ref.tagName = :tagName
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

    @Query("SELECT * FROM notes WHERE content LIKE '%[[' || :targetTitle || ']]%' AND id != :currentNoteId")
    fun getBacklinks(targetTitle: String, currentNoteId: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE title = :title LIMIT 1")
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
