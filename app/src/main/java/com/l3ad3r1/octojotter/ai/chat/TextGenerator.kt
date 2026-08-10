package com.l3ad3r1.octojotter.ai.chat

import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Streams generated text from a system + user prompt. Abstracted so the RAG
 * engine can be unit-tested without the arm64-only native runtime.
 */
interface TextGenerator {
    suspend fun isReady(): Boolean
    fun generate(system: String, user: String, maxTokens: Int = 512): Flow<String>
}

/**
 * llama.cpp-backed generator over the on-device [InferenceEngine] (Phase 0).
 *
 * Loads the GGUF once and reuses it; all generation is serialized behind a mutex
 * because the native engine is a single-threaded singleton. Requires an arm64
 * device — the native `ai-chat` library is not built for other ABIs.
 */
class LlamaTextGenerator(
    private val engine: InferenceEngine,
    private val modelFile: () -> File?,
) : TextGenerator {

    private val mutex = Mutex()
    @Volatile private var loadedPath: String? = null

    override suspend fun isReady(): Boolean = modelFile()?.exists() == true

    override fun generate(system: String, user: String, maxTokens: Int): Flow<String> = flow {
        val file = modelFile() ?: throw IllegalStateException("No chat model available")
        require(file.exists()) { "Chat model missing at ${file.absolutePath}" }
        mutex.withLock {
            if (loadedPath != file.absolutePath || !engine.state.value.isModelLoaded) {
                engine.loadModel(file.absolutePath)
                loadedPath = file.absolutePath
            }
            engine.setSystemPrompt(system)
            emitAll(engine.sendUserPrompt(user, predictLength = maxTokens))
        }
    }
}
