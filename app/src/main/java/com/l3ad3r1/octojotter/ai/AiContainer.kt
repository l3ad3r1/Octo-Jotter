package com.l3ad3r1.octojotter.ai

import android.content.Context
import com.l3ad3r1.octojotter.ai.embed.EmbeddingService
import com.l3ad3r1.octojotter.ai.embed.HashingBagOfWordsEmbeddingService
import com.l3ad3r1.octojotter.ai.embed.OnnxMiniLmEmbeddingService
import com.l3ad3r1.octojotter.ai.index.NoteChunker
import com.l3ad3r1.octojotter.ai.index.NoteIndexer
import com.l3ad3r1.octojotter.ai.index.VectorStore
import com.l3ad3r1.octojotter.ai.search.SemanticSearch
import com.l3ad3r1.octojotter.data.local.AppDatabase
import com.l3ad3r1.octojotter.data.local.NoteDao
import com.l3ad3r1.octojotter.data.local.NoteEmbeddingDao
import com.l3ad3r1.octojotter.data.local.NoteEntity
import java.io.File

/**
 * Manual composition root for on-device AI (the app has no DI framework).
 *
 * Wires the embedder, vector store, indexer and hybrid search from a Context.
 * Prefers the real ONNX MiniLM embedder when its model + vocab are on disk,
 * otherwise falls back to the deterministic bag-of-words embedder so the
 * pipeline still functions (lexical-only) — see [useRealEmbedder].
 */
class AiContainer private constructor(
    private val appContext: Context,
    val capability: AiCapability,
    private val noteDao: NoteDao,
    private val embeddingDao: NoteEmbeddingDao,
) {
    /** Directory where the MiniLM model + vocab are expected (download-on-first-use). */
    val modelDir: File = File(appContext.filesDir, "ai-models/minilm")
    private val modelFile: File get() = File(modelDir, "model.onnx")
    private val vocabFile: File get() = File(modelDir, "vocab.txt")

    /** True when the real ONNX model + tokenizer vocab are present on disk. */
    val useRealEmbedder: Boolean get() = modelFile.exists() && vocabFile.exists()

    val embedder: EmbeddingService by lazy {
        if (useRealEmbedder) {
            OnnxMiniLmEmbeddingService(modelFile = modelFile, vocabFile = vocabFile)
        } else {
            HashingBagOfWordsEmbeddingService()
        }
    }

    val vectorStore: VectorStore by lazy { VectorStore(embeddingDao) }

    val indexer: NoteIndexer by lazy {
        NoteIndexer(
            notes = DaoNoteSource(noteDao),
            embeddingDao = embeddingDao,
            embedder = embedder,
            chunker = NoteChunker(),
        )
    }

    val search: SemanticSearch by lazy {
        SemanticSearch(
            embedder = embedder,
            vectorStore = vectorStore,
            keyword = DaoKeywordSource(noteDao),
        )
    }

    private class DaoNoteSource(private val dao: NoteDao) : NoteIndexer.NoteSource {
        override suspend fun all(): List<NoteEntity> = dao.getAllNotes()
        override suspend fun byId(id: Int): NoteEntity? = dao.getNoteById(id)
    }

    private class DaoKeywordSource(private val dao: NoteDao) : SemanticSearch.KeywordSource {
        override suspend fun matchingNoteIds(query: String): Set<Int> {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return emptySet()
            return dao.getAllNotes()
                .asSequence()
                .filter { it.deletedAt == null }
                .filter { it.title.lowercase().contains(q) || it.content.lowercase().contains(q) }
                .map { it.id }
                .toSet()
        }
    }

    companion object {
        @Volatile private var instance: AiContainer? = null

        fun get(context: Context): AiContainer {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val app = context.applicationContext
                    val db = AppDatabase.getDatabase(app)
                    AiContainer(
                        appContext = app,
                        capability = AiCapability(app),
                        noteDao = db.noteDao(),
                        embeddingDao = db.noteEmbeddingDao(),
                    ).also { instance = it }
                }
            }
        }
    }
}
