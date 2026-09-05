package com.l3ad3r1.octojotter.ai

import android.content.Context
import com.l3ad3r1.octojotter.ai.embed.EmbeddingService
import com.l3ad3r1.octojotter.ai.embed.HashingBagOfWordsEmbeddingService
import com.l3ad3r1.octojotter.ai.chat.LlamaTextGenerator
import com.l3ad3r1.octojotter.ai.chat.RagChatEngine
import com.l3ad3r1.octojotter.ai.embed.OnnxMiniLmEmbeddingService
import com.l3ad3r1.octojotter.ai.index.NoteChunker
import com.l3ad3r1.octojotter.ai.index.NoteIndexer
import com.l3ad3r1.octojotter.ai.index.VectorStore
import com.l3ad3r1.octojotter.ai.model.ChatModel
import com.l3ad3r1.octojotter.ai.model.ModelCatalog
import com.l3ad3r1.octojotter.ai.model.ModelManager
import com.l3ad3r1.octojotter.data.local.AiPreferences
import com.l3ad3r1.ondevice.OnDeviceLlm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
 * are present ([ModelManager.isEmbeddingReady]) — otherwise the deterministic
 * bag-of-words fallback. Because the choice
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

    /** Directory the embedding model + vocab live in (app-private). */
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

    // --- RAG chat (Phase 2) ---

    private val aiPrefs = AiPreferences(appContext)
    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var selectedChatModelId: String = ModelCatalog.DEFAULT_CHAT.id

    init {
        // Keep the active chat model in sync with the user's persisted choice.
        containerScope.launch { aiPrefs.selectedChatModelId.collect { selectedChatModelId = it } }
    }

    /** The GGUF chat model the user selected (default: Llama 3.2 1B). */
    val chatModel: ChatModel get() = ModelCatalog.chatById(selectedChatModelId)

    /** True when the chat GGUF is on disk. */
    fun isChatModelReady(): Boolean = modelManager.isChatModelPresent(chatModel)

    /** The on-device inference engine. Created lazily — throws on non-arm64 when
     *  the native library can't load, so only touch this on capable devices. */
    private val inferenceEngine by lazy { OnDeviceLlm.engine(appContext) }

    /**
     * A RAG chat engine grounded in the user's notes. Only call on a device where
     * [AiCapability.supportsChat] is true and the chat model is present.
     */
    fun ragChat(): RagChatEngine {
        val generator = LlamaTextGenerator(
            engine = inferenceEngine,
            modelFile = { modelManager.storage.chatModelFile(chatModel.file.fileName).takeIf { it.exists() } },
        )
        return RagChatEngine(
            embedder = embedder(),
            vectorStore = vectorStore,
            generator = generator,
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
