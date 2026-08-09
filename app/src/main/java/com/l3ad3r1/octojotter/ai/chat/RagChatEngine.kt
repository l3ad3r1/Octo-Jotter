package com.l3ad3r1.octojotter.ai.chat

import com.l3ad3r1.octojotter.ai.embed.EmbeddingService
import com.l3ad3r1.octojotter.ai.index.VectorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Retrieval-augmented chat over the user's notes (spec §6.3).
 *
 * Pipeline: embed the question -> retrieve the most similar note chunks from the
 * [VectorStore] -> assemble a grounded prompt ([RagPrompt]) -> stream the answer
 * from the on-device [TextGenerator]. The answer is grounded only in the user's
 * own notes and everything runs on-device.
 */
class RagChatEngine(
    private val embedder: EmbeddingService,
    private val vectorStore: VectorStore,
    private val generator: TextGenerator,
    private val retrieveK: Int = 6,
) {
    /** Retrieve grounding + assembled prompt for a question (no generation). */
    suspend fun retrieve(question: String): RagContext {
        val q = question.trim()
        if (q.isEmpty()) return RagContext(system = "", question = "", citations = emptyList())
        val queryVector = embedder.embed(q)
        val chunks = vectorStore.topKChunks(queryVector, k = retrieveK, modelId = embedder.modelId)
        return RagPrompt.build(q, chunks)
    }

    /**
     * Ask a question. Emits [RagEvent.Sources] first (the retrieved notes), then
     * a stream of [RagEvent.Token]s, then [RagEvent.Done] — or [RagEvent.Error].
     */
    fun ask(question: String, maxTokens: Int = 512): Flow<RagEvent> = flow {
        val q = question.trim()
        if (q.isEmpty()) {
            emit(RagEvent.Error("Please enter a question."))
            return@flow
        }
        if (!generator.isReady()) {
            emit(RagEvent.Error("The chat model hasn't been downloaded yet."))
            return@flow
        }
        val context = retrieve(q)
        emit(RagEvent.Sources(context.citations))
        emitAll(generator.generate(context.system, context.question, maxTokens).map { RagEvent.Token(it) })
        emit(RagEvent.Done)
    }.catch { t ->
        emit(RagEvent.Error(t.message ?: "Generation failed."))
    }.flowOn(Dispatchers.Default)
}
