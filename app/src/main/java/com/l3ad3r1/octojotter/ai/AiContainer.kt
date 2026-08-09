package com.l3ad3r1.octojotter.ai

import android.content.Context
import com.l3ad3r1.octojotter.ai.embed.EmbeddingService
import com.l3ad3r1.octojotter.ai.embed.HashingBagOfWordsEmbeddingService
import com.l3ad3r1.octojotter.ai.embed.OnnxMiniLmEmbeddingService
import com.l3ad3r1.octojotter.ai.index.NoteChunker
import com.l3ad3r1.octojotter.ai.index.NoteIndexer
import com.l3ad3r1.octojotter.ai.index.VectorStore
import com.l3ad3r1.octojotter.ai.model.ModelCatalog
import com.l3ad3r1.octojotter.ai.model.ModelManager
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
 * The embedder is chosen dynamically: the real ONNX MiniLM model when its files
 * are present ([ModelManager.isEmbeddingReady]) — which includes a copy shared by
 * Hermes — otherwise the deterministic bag-of-words fallback. Because the choice
 * is re-evaluated per access, a download takes effect without an app restart.
 */
class AiContainer private constructor(
    private val appContext: Context,
    val capability: AiCapability,
    private val noteDao: NoteDao,
    private val embeddingDao: NoteEmbeddingDao,
) {
    val modelManager: ModelManager = ModelManager(appContext)
    private val embeddingModel = ModelCatalog.EMBEDDING

    /** Directory the embedding model + vocab live in (shared with Hermes when granted). */
    val modelDir: File get() = modelManager.storage.embeddingDir(embeddingModel.id)
    private val modelFile: File get() = File(modelDir, embeddingModel.model.fileName)
    private val vocabFile: File get() = File(modelDir, embeddingModel.vocab.fileName)

    /** True when the real ONNX embedder is usable (files present). */
    val useRealEmbedder: Boolean get() = modelManager.isEmbeddingReady(embeddingModel)

    private val fallbackEmbedder by lazy { HashingBagOfWordsEmbeddingService(embeddingModel.dimension) }
    @Volatile private var realEmbedder: OnnxMiniLmEmbeddingService? = null

    /** The embedder to use right now — real if the model is present, else fallback. */
    fun embedder(): EmbeddingService {
        if (useRealEmbedder) {
            realEmbedder?.let { return it }
            return synchronized(this) {
                realEmbedder ?: OnnxMiniLmEmbeddingService(
                    modelFile = modelFile,
                    vocabFile = vocabFile,
                    dimension = embeddingModel.dimension,
                ).also { realEmbedder = it }
            }
        }
        return fallbackEmbedder
    }

    val vectorStore: VectorStore by lazy { VectorStore(embeddingDao) }

    fun indexer(): NoteIndexer = NoteIndexer(
        notes = DaoNoteSource(noteDao),
        embeddingDao = embeddingDao,
        embedder = embedder(),
        chunker = NoteChunker(),
    )

    fun search(): SemanticSearch = SemanticSearch(
        embedder = embedder(),
        vectorStore = vectorStore,
        keyword = DaoKeywordSource(noteDao),
    )

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
