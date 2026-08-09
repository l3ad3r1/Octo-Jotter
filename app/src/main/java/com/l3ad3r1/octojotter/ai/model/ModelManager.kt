package com.l3ad3r1.octojotter.ai.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Downloads model files on first use and reuses any already present — including
 * files a sibling app (Hermes) downloaded into the shared `AI Models` folder.
 *
 * Downloads are resumable (HTTP Range), streamed to a `.part` file that is
 * size-verified and only then renamed into place, so a crash mid-download never
 * leaves a corrupt model that would fail to load.
 */
class ModelManager(
    private val context: Context,
    val storage: ModelStorage = ModelStorage(context),
) {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS) // large downloads — no overall cap
            .build()
    }

    data class Progress(val bytesDownloaded: Long, val totalBytes: Long?) {
        val fraction: Float? get() = totalBytes?.takeIf { it > 0 }?.let { bytesDownloaded.toFloat() / it }
    }

    sealed interface Result {
        data class Success(val file: File) : Result
        data class AlreadyPresent(val file: File) : Result
        data class Failure(val message: String) : Result
    }

    /** A file is present if it exists and matches the expected size (or is non-empty). */
    fun isPresent(file: DownloadableFile, dir: File): Boolean =
        isFilePresent(File(dir, file.fileName), file.sizeBytes)

    /** Is the whole embedding bundle (model + vocab) available? */
    fun isEmbeddingReady(model: EmbeddingModel = ModelCatalog.EMBEDDING): Boolean {
        val dir = storage.embeddingDir(model.id)
        return isPresent(model.model, dir) && isPresent(model.vocab, dir)
    }

    /** Is a chat GGUF present (possibly downloaded by Hermes)? */
    fun isChatModelPresent(model: ChatModel): Boolean =
        isPresent(model.file, storage.chatModelsDir())

    /** Download the embedding bundle (model + vocab), reporting combined progress. */
    suspend fun downloadEmbeddingModel(
        model: EmbeddingModel = ModelCatalog.EMBEDDING,
        onProgress: (Progress) -> Unit = {},
    ): Result {
        val dir = storage.embeddingDir(model.id)
        val total = model.totalBytes
        var base = 0L
        for (part in listOf(model.model, model.vocab)) {
            val r = download(part, dir, totalOverride = total, baseOffset = base) { p ->
                onProgress(Progress(p.bytesDownloaded, total))
            }
            when (r) {
                is Result.Failure -> return r
                is Result.Success, is Result.AlreadyPresent -> base += part.sizeBytes ?: 0L
            }
        }
        return Result.Success(File(dir, model.model.fileName))
    }

    /** Download a chat GGUF into the shared models dir (Hermes-compatible). */
    suspend fun downloadChatModel(
        model: ChatModel,
        onProgress: (Progress) -> Unit = {},
    ): Result = download(model.file, storage.chatModelsDir(), onProgress = onProgress)

    /**
     * Core resumable download of a single file into [dir].
     * [totalOverride]/[baseOffset] let a multi-file bundle report combined progress.
     */
    suspend fun download(
        file: DownloadableFile,
        dir: File,
        totalOverride: Long? = null,
        baseOffset: Long = 0L,
        onProgress: (Progress) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = File(dir, file.fileName)
        if (isPresent(file, dir)) return@withContext Result.AlreadyPresent(target)

        val part = File(dir, file.fileName + ".part")
        var existing = if (part.isFile) part.length() else 0L

        // Free-space pre-check (best effort) against the remaining bytes.
        file.sizeBytes?.let { size ->
            val remaining = (size - existing).coerceAtLeast(0)
            if (storage.usableSpaceBytes(dir) < remaining + SAFETY_MARGIN) {
                return@withContext Result.Failure("Not enough free space for ${file.sizeLabel}")
            }
        }

        val reqBuilder = Request.Builder().url(file.url)
        if (existing > 0) reqBuilder.header("Range", "bytes=$existing-")

        try {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.Failure("HTTP ${resp.code} for ${file.fileName}")
                }
                // Server ignored our Range (200 not 206) → restart from scratch.
                if (existing > 0 && resp.code != 206) {
                    part.delete(); existing = 0
                }
                val body = resp.body ?: return@withContext Result.Failure("Empty body for ${file.fileName}")

                RandomAccessFile(part, "rw").use { out ->
                    out.seek(existing)
                    body.byteStream().use { input ->
                        val buf = ByteArray(1 shl 16)
                        var downloaded = existing
                        while (true) {
                            coroutineContext.ensureActive() // cooperative cancellation
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            downloaded += n
                            onProgress(Progress(baseOffset + downloaded, totalOverride ?: file.sizeBytes))
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            coroutineContext.ensureActive()
            return@withContext Result.Failure(t.message ?: "download failed")
        }

        // Verify size then atomically rename into place.
        file.sizeBytes?.let { expected ->
            if (part.length() != expected) {
                part.delete()
                return@withContext Result.Failure(
                    "Size mismatch for ${file.fileName}: got ${part.length()}, expected $expected"
                )
            }
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            return@withContext Result.Failure("Could not finalize ${file.fileName}")
        }
        Result.Success(target)
    }

    companion object {
        private const val SAFETY_MARGIN = 64L * 1024 * 1024 // keep 64 MB headroom

        /** Pure presence check (no Context) — present if the file exists and matches
         *  [expectedSize], or is non-empty when the size is unknown. */
        fun isFilePresent(file: File, expectedSize: Long?): Boolean {
            if (!file.isFile) return false
            return expectedSize?.let { file.length() == it } ?: (file.length() > 0)
        }
    }
}
