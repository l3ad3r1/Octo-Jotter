package com.l3ad3r1.octojotter.ai.index

import com.l3ad3r1.octojotter.ai.embed.EmbeddingService
import com.l3ad3r1.octojotter.ai.embed.FloatVectors
import com.l3ad3r1.octojotter.data.local.NoteEmbeddingDao
import com.l3ad3r1.octojotter.data.local.NoteEmbeddingEntity
import com.l3ad3r1.octojotter.data.local.NoteEntity
import java.security.MessageDigest

/**
 * Builds and maintains the vector index for semantic search (spec §6.2, §9).
 *
 * Indexing is incremental: a note whose chunk-hash set is unchanged is skipped.
 * Notes that are soft-deleted, locked, or encrypted are NOT indexed and any
 * existing rows for them are removed (privacy invariant P2 — locked/encrypted
 * content stays out of the plaintext index).
 */
class NoteIndexer(
    private val notes: NoteSource,
    private val embeddingDao: NoteEmbeddingDao,
    private val embedder: EmbeddingService,
    private val chunker: NoteChunker = NoteChunker(),
) {
    /** Minimal read surface over notes, so this is unit-testable without Room. */
    interface NoteSource {
        suspend fun all(): List<NoteEntity>
        suspend fun byId(id: Int): NoteEntity?
    }

    data class Result(val indexed: Int, val skipped: Int, val removed: Int)

    /** (Re)index a single note. Returns true if it now has embeddings. */
    suspend fun indexNote(noteId: Int): Boolean {
        val note = notes.byId(noteId) ?: run { embeddingDao.deleteForNote(noteId); return false }
        return indexNote(note)
    }

    private suspend fun indexNote(note: NoteEntity): Boolean {
        if (!isIndexable(note)) {
            embeddingDao.deleteForNote(note.id)
            return false
        }
        val chunks = chunker.chunk(note.content)
        if (chunks.isEmpty()) {
            embeddingDao.deleteForNote(note.id)
            return false
        }
        val newHashes = chunks.map { sha256(it) }
        val existing = embeddingDao.hashesForNote(note.id).toSet()
        if (existing.size == newHashes.size && existing.containsAll(newHashes)) {
            return true // unchanged — skip re-embedding
        }
        val vectors = embedder.embedAll(chunks)
        val rows = chunks.mapIndexed { i, text ->
            NoteEmbeddingEntity(
                noteId = note.id,
                chunkIndex = i,
                chunkText = text,
                vector = FloatVectors.toBytes(vectors[i]),
                contentHash = newHashes[i],
                model = embedder.modelId,
            )
        }
        embeddingDao.replaceForNote(note.id, rows)
        return true
    }

    /** Full pass over every note. Cheap when most notes are unchanged. */
    suspend fun indexAll(): Result {
        if (!embedder.isReady()) return Result(0, 0, 0)
        // Drop rows left by a previous embedder before re-indexing.
        embeddingDao.deleteStaleModels(embedder.modelId)

        var indexed = 0
        var skipped = 0
        var removed = 0
        val liveIds = HashSet<Int>()
        for (note in notes.all()) {
            if (!isIndexable(note)) {
                embeddingDao.deleteForNote(note.id)
                removed++
                continue
            }
            liveIds += note.id
            val before = embeddingDao.hashesForNote(note.id).size
            val ok = indexNote(note)
            when {
                !ok -> removed++
                before > 0 && embeddingDao.hashesForNote(note.id).size == before -> skipped++
                else -> indexed++
            }
        }
        // Prune orphans (notes that vanished entirely).
        for (id in embeddingDao.indexedNoteIds()) {
            if (id !in liveIds) { embeddingDao.deleteForNote(id); removed++ }
        }
        return Result(indexed, skipped, removed)
    }

    private fun isIndexable(note: NoteEntity): Boolean =
        note.deletedAt == null && !note.locked && !note.encrypted

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return buildString(digest.size * 2) { for (b in digest) append("%02x".format(b)) }
    }
}
