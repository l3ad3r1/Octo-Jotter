package com.l3ad3r1.octojotter.ai.model

import android.content.Context
import java.io.File

/**
 * Resolves where on-device model files live.
 *
 * Everything goes in Octo Jotter's own external files dir
 * (`Android/data/<pkg>/files/AI Models`), which needs no storage permission.
 *
 * This used to point at the public `<external-storage>/AI Models/` folder when
 * the user granted All-Files-Access, so a GGUF downloaded by the sibling Hermes
 * app was reused instead of downloaded twice. That sharing is gone: Play Store
 * restricts `MANAGE_EXTERNAL_STORAGE` to a short list of qualifying use cases
 * (file managers, backup, anti-virus), and "reuse a sibling app's model files"
 * is not one of them. Models are now downloaded per-app.
 */
class ModelStorage(private val context: Context) {

    /** Root for all model files — app-private, no permission required. */
    fun modelsRoot(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, MODELS_DIR_NAME)

    /** Directory GGUF chat models live in. */
    fun chatModelsDir(): File = modelsRoot().apply { mkdirs() }

    /** Directory for an embedding model bundle (model + vocab). */
    fun embeddingDir(id: String): File =
        File(chatModelsDir(), "embeddings/$id").apply { mkdirs() }

    /** Full path a chat model file resolves to. */
    fun chatModelFile(fileName: String): File = File(chatModelsDir(), fileName)

    /** Usable free bytes on the filesystem backing [dir]. */
    fun usableSpaceBytes(dir: File): Long =
        runCatching { (dir.takeIf { it.exists() } ?: dir.parentFile)?.usableSpace ?: 0L }.getOrDefault(0L)

    companion object {
        /** Folder name under the app's external files dir. */
        const val MODELS_DIR_NAME = "AI Models"
    }
}
