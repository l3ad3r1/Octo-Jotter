package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.ai.embed.HashingBagOfWordsEmbeddingService
import com.l3ad3r1.octojotter.ai.index.NoteIndexer
import com.l3ad3r1.octojotter.ai.index.VectorStore
import com.l3ad3r1.octojotter.ai.search.SemanticSearch
import com.l3ad3r1.octojotter.data.local.NoteEmbeddingDao
import com.l3ad3r1.octojotter.data.local.NoteEmbeddingEntity
import com.l3ad3r1.octojotter.data.local.NoteEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory fake of the Room DAO for pipeline tests. */
private class FakeEmbeddingDao : NoteEmbeddingDao {
    val rows = mutableListOf<NoteEmbeddingEntity>()
    private var seq = 1L
    override suspend fun allActive() = rows.toList()
    override suspend fun forNote(noteId: Int) = rows.filter { it.noteId == noteId }.sortedBy { it.chunkIndex }
    override suspend fun hashesForNote(noteId: Int) = rows.filter { it.noteId == noteId }.map { it.contentHash }
    override suspend fun insertAll(rows: List<NoteEmbeddingEntity>) {
        rows.forEach { this.rows += it.copy(id = seq++) }
    }
    override suspend fun deleteForNote(noteId: Int) { rows.removeAll { it.noteId == noteId } }
    override suspend fun deleteStaleModels(currentModel: String) { rows.removeAll { it.model != currentModel } }
    override suspend fun count() = rows.size
    override suspend fun indexedNoteIds() = rows.map { it.noteId }.distinct()
}

private class ListNoteSource(private val notes: List<NoteEntity>) : NoteIndexer.NoteSource {
    override suspend fun all() = notes
    override suspend fun byId(id: Int) = notes.firstOrNull { it.id == id }
}

class SemanticSearchTest {

    private fun note(id: Int, title: String, content: String) =
        NoteEntity(id = id, title = title, content = content)

    @Test
    fun `index then hybrid search ranks the relevant note first`() = runBlocking {
        val notes = listOf(
            note(1, "Cephalopods", "The octopus is a cephalopod living in the ocean, highly intelligent."),
            note(2, "Budget", "Monthly banking finance spreadsheet with rent and grocery expenses."),
            note(3, "Recipe", "A pasta recipe with tomato garlic and basil for dinner."),
        )
        val dao = FakeEmbeddingDao()
        val embedder = HashingBagOfWordsEmbeddingService()

        val result = NoteIndexer(ListNoteSource(notes), dao, embedder).indexAll()
        assertTrue("expected notes indexed", result.indexed >= 3)
        assertTrue(dao.count() >= 3)

        val keyword = object : SemanticSearch.KeywordSource {
            override suspend fun matchingNoteIds(query: String): Set<Int> {
                val q = query.lowercase()
                return notes.filter {
                    it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
                }.map { it.id }.toSet()
            }
        }
        val search = SemanticSearch(embedder, VectorStore(dao), keyword)

        val hits = search.search("octopus ocean cephalopod", k = 3)
        assertTrue(hits.isNotEmpty())
        assertEquals("note 1 should rank first", 1, hits.first().noteId)
        assertTrue("top result should have a positive semantic score", hits.first().semanticScore > 0f)
    }

    @Test
    fun `reindex is incremental — unchanged notes are skipped`() = runBlocking {
        val notes = listOf(note(1, "A", "alpha beta gamma delta"))
        val dao = FakeEmbeddingDao()
        val embedder = HashingBagOfWordsEmbeddingService()
        val indexer = NoteIndexer(ListNoteSource(notes), dao, embedder)

        val first = indexer.indexAll()
        assertEquals(1, first.indexed)
        val second = indexer.indexAll()
        assertEquals("second pass should skip the unchanged note", 1, second.skipped)
        assertEquals(0, second.indexed)
    }

    @Test
    fun `locked and encrypted notes are not indexed`() = runBlocking {
        val notes = listOf(
            NoteEntity(id = 1, title = "secret", content = "classified content", locked = true),
            NoteEntity(id = 2, title = "enc", content = "ciphertext", encrypted = true),
            NoteEntity(id = 3, title = "ok", content = "plain visible note"),
        )
        val dao = FakeEmbeddingDao()
        NoteIndexer(ListNoteSource(notes), dao, HashingBagOfWordsEmbeddingService()).indexAll()
        assertEquals(listOf(3), dao.indexedNoteIds())
    }
}
