package com.l3ad3r1.octojotter.ai.embed

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer

/**
 * On-device sentence embeddings via ONNX Runtime + all-MiniLM-L6-v2 (spec §6.1).
 *
 * The model (`model.onnx`) and its `vocab.txt` are supplied on disk — downloaded
 * on first use (ModelManager, a later slice) or pushed for testing. Until both
 * files exist [isReady] returns false and callers should fall back to keyword
 * search. Output is mean-pooled over tokens (attention-masked) then L2-normalized
 * so cosine reduces to a dot product.
 *
 * Inference is serialized behind a mutex — one OrtSession, one call at a time.
 */
class OnnxMiniLmEmbeddingService(
    private val modelFile: File,
    private val vocabFile: File,
    override val modelId: String = "all-MiniLM-L6-v2-int8",
    override val dimension: Int = 384,
    private val maxTokens: Int = 256,
) : EmbeddingService {

    private val mutex = Mutex()
    @Volatile private var session: OrtSession? = null
    @Volatile private var tokenizer: WordPieceTokenizer? = null
    @Volatile private var inputNames: Set<String> = emptySet()

    override suspend fun isReady(): Boolean = modelFile.exists() && vocabFile.exists()

    private suspend fun ensureLoaded() {
        if (session != null && tokenizer != null) return
        mutex.withLock {
            if (session != null && tokenizer != null) return
            check(modelFile.exists()) { "Embedding model missing at ${modelFile.absolutePath}" }
            check(vocabFile.exists()) { "Tokenizer vocab missing at ${vocabFile.absolutePath}" }
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions()
            val s = env.createSession(modelFile.absolutePath, opts)
            session = s
            inputNames = s.inputNames.toSet()
            tokenizer = WordPieceTokenizer(
                vocab = vocabFile.inputStream().use { WordPieceTokenizer.loadVocab(it) },
                maxTokens = maxTokens,
            )
        }
    }

    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        ensureLoaded()
        val tok = tokenizer!!
        val enc = tok.encode(text)
        mutex.withLock { runSession(enc) }
    }

    private fun runSession(enc: WordPieceTokenizer.Encoding): FloatArray {
        val env = OrtEnvironment.getEnvironment()
        val s = session!!
        val seq = enc.size
        val shape = longArrayOf(1, seq.toLong())

        val tensors = HashMap<String, OnnxTensor>()
        try {
            fun put(name: String, data: LongArray) {
                if (name in inputNames) {
                    tensors[name] = OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape)
                }
            }
            put("input_ids", enc.ids)
            put("attention_mask", enc.attentionMask)
            put("token_type_ids", LongArray(seq)) // all zeros for a single sequence

            s.run(tensors).use { result ->
                @Suppress("UNCHECKED_CAST")
                val hidden = (result[0].value as Array<Array<FloatArray>>)[0] // [seq][dim]
                return meanPool(hidden, enc.attentionMask)
            }
        } finally {
            tensors.values.forEach { it.close() }
        }
    }

    /** Attention-masked mean pooling over token embeddings, then L2-normalize. */
    private fun meanPool(hidden: Array<FloatArray>, mask: LongArray): FloatArray {
        val dim = hidden.firstOrNull()?.size ?: dimension
        val sum = FloatArray(dim)
        var count = 0f
        for (t in hidden.indices) {
            if (mask[t] == 0L) continue
            count += 1f
            val row = hidden[t]
            for (d in 0 until dim) sum[d] += row[d]
        }
        if (count > 0f) for (d in 0 until dim) sum[d] /= count
        return FloatVectors.normalize(sum)
    }

    fun close() {
        session?.close()
        session = null
        tokenizer = null
    }
}
