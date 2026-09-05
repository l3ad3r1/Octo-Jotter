package com.l3ad3r1.octojotter.ai.model

/**
 * One downloadable file (a model or a tokenizer vocab).
 *
 * @param fileName on-disk identity — used to detect "already downloaded".
 * @param url      direct HuggingFace `resolve` URL (302s to the CDN).
 * @param sizeBytes verified download size; drives the free-space pre-check and
 *                 the "already downloaded" check. Null = size unknown (fall back
 *                 to existence + non-empty).
 */
data class DownloadableFile(
    val fileName: String,
    val url: String,
    val sizeBytes: Long?,
) {
    val sizeLabel: String
        get() {
            val bytes = sizeBytes ?: return "unknown size"
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1024) String.format("%.1f GB", mb / 1024.0) else String.format("%.0f MB", mb)
        }
}

/** A GGUF chat model (llama.cpp). */
data class ChatModel(
    val id: String,
    val displayName: String,
    val file: DownloadableFile,
)

/** An embedding model bundle: the ONNX model plus its WordPiece vocab. */
data class EmbeddingModel(
    val id: String,
    val displayName: String,
    val model: DownloadableFile,
    val vocab: DownloadableFile,
    val dimension: Int,
) {
    val totalBytes: Long? =
        model.sizeBytes?.let { m -> vocab.sizeBytes?.let { v -> m + v } }

    val totalSizeLabel: String
        get() {
            val bytes = totalBytes ?: return "unknown size"
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1024) String.format("%.1f GB", mb / 1024.0) else String.format("%.0f MB", mb)
        }
}

/**
 * The registry of downloadable models.
 *
 * Chat models are all bartowski Q4_K_M quantisations. Their ids and filenames
 * originally mirrored the sibling Hermes app so downloads could be shared; that
 * sharing is gone (see [ModelStorage]) but the names are kept so an existing
 * install's downloads are still recognised.
 *
 * Sizes were verified live against HuggingFace before shipping.
 */
object ModelCatalog {

    val CHAT_MODELS: List<ChatModel> = listOf(
        ChatModel(
            id = "llama-3.2-1b-q4km",
            displayName = "Llama 3.2 1B Instruct (Q4_K_M)",
            file = DownloadableFile(
                fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                sizeBytes = 807_694_464L,
            ),
        ),
        ChatModel(
            id = "qwen2.5-1.5b-q4km",
            displayName = "Qwen2.5 1.5B Instruct (Q4_K_M)",
            file = DownloadableFile(
                fileName = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                url = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                sizeBytes = 986_048_768L,
            ),
        ),
        ChatModel(
            id = "qwen2.5-3b-q4km",
            displayName = "Qwen2.5 3B Instruct (Q4_K_M)",
            file = DownloadableFile(
                fileName = "Qwen2.5-3B-Instruct-Q4_K_M.gguf",
                url = "https://huggingface.co/bartowski/Qwen2.5-3B-Instruct-GGUF/resolve/main/Qwen2.5-3B-Instruct-Q4_K_M.gguf",
                sizeBytes = 1_929_903_264L,
            ),
        ),
        ChatModel(
            id = "llama-3.2-3b-q4km",
            displayName = "Llama 3.2 3B Instruct (Q4_K_M)",
            file = DownloadableFile(
                fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                url = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                sizeBytes = 2_019_377_696L,
            ),
        ),
    )

    /** Default chat model when the user has never picked one. */
    val DEFAULT_CHAT: ChatModel = CHAT_MODELS.first()

    fun chatById(id: String): ChatModel = CHAT_MODELS.firstOrNull { it.id == id } ?: DEFAULT_CHAT

    /** all-MiniLM-L6-v2 int8 ONNX + vocab. Sizes verified live (Xenova export). */
    val EMBEDDING: EmbeddingModel = EmbeddingModel(
        id = "all-MiniLM-L6-v2",
        displayName = "all-MiniLM-L6-v2 (int8)",
        model = DownloadableFile(
            fileName = "model.onnx",
            url = "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx",
            sizeBytes = 22_972_370L,
        ),
        vocab = DownloadableFile(
            fileName = "vocab.txt",
            url = "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/vocab.txt",
            sizeBytes = 231_508L,
        ),
        dimension = 384,
    )
}
