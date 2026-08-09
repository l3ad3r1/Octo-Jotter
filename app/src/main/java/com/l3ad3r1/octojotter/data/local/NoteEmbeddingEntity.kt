package com.l3ad3r1.octojotter.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One embedded chunk of a note (Phase 1 of docs/ON-DEVICE-AI.md).
 *
 * A note has 0..N chunks. [vector] is a little-endian float BLOB (see
 * FloatVectors); [contentHash] lets the indexer skip re-embedding unchanged
 * chunks, and [model] lets an embedder swap invalidate only stale rows. Rows
 * cascade-delete with their note.
 *
 * This table is local-only and fully rebuildable — it is never synced.
 */
@Entity(
    tableName = "note_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class NoteEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Int,
    val chunkIndex: Int,
    val chunkText: String,
    val vector: ByteArray,
    val contentHash: String,
    val model: String,
    val embeddedAt: Long = System.currentTimeMillis(),
) {
    // ByteArray needs value-based equals/hashCode for correct data-class semantics.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NoteEmbeddingEntity) return false
        return id == other.id &&
            noteId == other.noteId &&
            chunkIndex == other.chunkIndex &&
            chunkText == other.chunkText &&
            vector.contentEquals(other.vector) &&
            contentHash == other.contentHash &&
            model == other.model &&
            embeddedAt == other.embeddedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + noteId
        result = 31 * result + chunkIndex
        result = 31 * result + chunkText.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + contentHash.hashCode()
        result = 31 * result + model.hashCode()
        result = 31 * result + embeddedAt.hashCode()
        return result
    }
}
