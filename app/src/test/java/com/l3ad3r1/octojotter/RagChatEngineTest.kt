package com.l3ad3r1.octojotter

import com.l3ad3r1.octojotter.ai.chat.RagChatEngine
import com.l3ad3r1.octojotter.ai.chat.RagEvent
import com.l3ad3r1.octojotter.ai.chat.TextGenerator
import com.l3ad3r1.octojotter.ai.embed.HashingBagOfWordsEmbeddingService
import com.l3ad3r1.octojotter.ai.index.NoteIndexer
import com.l3ad3r1.octojotter.ai.index.VectorStore
import com.l3ad3r1.octojotter.data.local.NoteEmbeddingDao
import com.l3ad3r1.octojotter.data.local.NoteEmbeddingEntity
import com.l3ad3r1.octojotter.data.local.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class RagFakeDao : NoteEmbeddingDao {
    val rows = mutableListOf<NoteEmbeddingEntity>()
    private var seq = 1L
    override suspend fun allActive() = rows.toList()
    override suspend fun forNote(noteId: Int) = rows.filter { it.noteId == noteId }
    override suspend fun hashesForNote(noteId: Int) = rows.filter { it.noteId == noteId }.map { it.contentHash }
    override suspend fun insertAll(rows: List<NoteEmbeddingEntity>) { rows.forEach { this.rows += it.copy(id = seq++) } }
    override suspend fun deleteForNote(noteId: Int) { rows.removeAll { it.noteId == noteId } }
    override suspend fun deleteStaleModels(currentModel: String) { rows.removeAll { it.model != currentModel } }
    override suspend fun count() = rows.size
    override suspend fun indexedNoteIds() = rows.map { it.noteId }.distinct()
}

private class RagNoteSource(private val notes: List<NoteEntity>) : NoteIndexer.NoteSource {
    override suspend fun all() = notes
    override suspend fun byId(id: Int) = notes.firstOrNull { it.id == id }
}

/** Records the prompt it was handed and streams a canned answer word-by-word. */
private class FakeGenerator(private val ready: Boolean = true) : TextGenerator {
    var lastSystem: String? = null
    var lastUser: String? = null
    override suspend fun isReady() = ready
    override fun generate(system: String, user: String, maxTokens: Int): Flow<String> = flow {
        lastSystem = system
        lastUser = user
        "The octopus is intelligent .".split(" ").forEach { emit("$it ") }
    }
}

class RagChatEngineTest {

    private fun seededEngine(gen: TextGenerator): Pair<RagChatEngine, RagFakeDao> {
        val notes = listOf(
            NoteEntity(id = 1, title = "Sea", content = "The octopus is an intelligent cephalopod in the ocean."),
            NoteEntity(id = 2, title = "Money", content = "Quarterly budget rent salary and bank loans."),
        )
        val dao = RagFakeDao()
        val embedder = HashingBagOfWordsEmbeddingService()
        runBlocking { NoteIndexer(RagNoteSource(notes), dao, embedder).indexAll() }
        return RagChatEngine(embedder, VectorStore(dao), gen) to dao
    }

    @Test
    fun `ask emits sources then tokens then done, grounded in the right note`() = runBlocking {
        val gen = FakeGenerator(ready = true)
        val (engine, _) = seededEngine(gen)

        val events = engine.ask("octopus ocean cephalopod").toList()

        val sources = events.filterIsInstance<RagEvent.Sources>().single()
        assertTrue("note 1 should be a source", sources.citations.any { it.noteId == 1 })
        assertEquals(sources.citations.first().noteId, 1) // best match first

        assertTrue(events.any { it is RagEvent.Token })
        assertTrue(events.last() is RagEvent.Done)

        // The generator was handed a grounded prompt citing the octopus note.
        assertTrue(gen.lastSystem!!.contains("[Note 1]"))
        assertTrue(gen.lastSystem!!.contains("octopus"))
    }

    @Test
    fun `ask reports an error when the model is not ready`() = runBlocking {
        val (engine, _) = seededEngine(FakeGenerator(ready = false))
        val events = engine.ask("anything").toList()
        assertTrue(events.single() is RagEvent.Error)
    }

    @Test
    fun `blank question is rejected`() = runBlocking {
        val (engine, _) = seededEngine(FakeGenerator(ready = true))
        val events = engine.ask("   ").toList()
        assertTrue(events.single() is RagEvent.Error)
    }
}
