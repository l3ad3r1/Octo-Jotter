package com.l3ad3r1.octojotter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface NoteEmbeddingDao {

    /** All embedding rows for notes that are not soft-deleted. Loaded into memory
     *  for brute-force cosine search (fine for realistic vaults; see spec §6.2). */
    @Query(
        """
        SELECT ne.* FROM note_embeddings ne
        INNER JOIN notes n ON n.id = ne.noteId
        WHERE n.deletedAt IS NULL
        """
    )
    suspend fun allActive(): List<NoteEmbeddingEntity>

    @Query("SELECT * FROM note_embeddings WHERE noteId = :noteId ORDER BY chunkIndex")
    suspend fun forNote(noteId: Int): List<NoteEmbeddingEntity>

    /** Content hashes currently stored for a note, to detect unchanged chunks. */
    @Query("SELECT contentHash FROM note_embeddings WHERE noteId = :noteId")
    suspend fun hashesForNote(noteId: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<NoteEmbeddingEntity>)

    @Query("DELETE FROM note_embeddings WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Int)

    /** Drop rows produced by a superseded embedder (after a model swap). */
    @Query("DELETE FROM note_embeddings WHERE model != :currentModel")
    suspend fun deleteStaleModels(currentModel: String)

    @Query("SELECT COUNT(*) FROM note_embeddings")
    suspend fun count(): Int

    @Query("SELECT DISTINCT noteId FROM note_embeddings")
    suspend fun indexedNoteIds(): List<Int>

    /** Replace all chunks for a note atomically (delete-then-insert). */
    @Transaction
    suspend fun replaceForNote(noteId: Int, rows: List<NoteEmbeddingEntity>) {
        deleteForNote(noteId)
        if (rows.isNotEmpty()) insertAll(rows)
    }
}
