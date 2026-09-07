package com.l3ad3r1.octojotter.ai.model

import android.content.Context
import android.os.Build
import android.os.Environment
import com.l3ad3r1.octojotter.BuildConfig
import java.io.File

/**
 * Resolves where on-device model files live.
 *
 * Downloads always land in Octo Jotter's own external files dir
 * (`Android/data/<pkg>/files/AI Models`), which needs no storage permission —
 * that never changes, regardless of what's below.
 *
 * Detecting — and loading — an *already-present* model additionally checks
 * the public `<external-storage>/AI Models/` folder, where a file manager or
 * the sibling Hermes app's older convention would have put one, but only on
 * the `github` flavour and only once the user has granted All Files Access.
 * Play restricts `MANAGE_EXTERNAL_STORAGE` to a short list of qualifying use
 * cases that this isn't part of, so the `play` flavour never declares the
 * permission and [hasAllFilesAccess] is unconditionally false there.
 */
class ModelStorage(private val context: Context) {

    /** Root for all model files — app-private, no permission required. Downloads always go here. */
    fun modelsRoot(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, MODELS_DIR_NAME)

    /** Directory GGUF chat models download into — always app-private. */
    fun chatModelsDir(): File = modelsRoot().apply { mkdirs() }

    /** Directory an embedding bundle (model + vocab) downloads into — always app-private. */
    fun embeddingDir(id: String): File = File(chatModelsDir(), "embeddings/$id").apply { mkdirs() }

    /** The app-private path a chat model file downloads to. */
    fun chatModelFile(fileName: String): File = File(chatModelsDir(), fileName)

    /** The public shared folder — readable only with All Files Access. */
    fun publicModelsRoot(): File = File(Environment.getExternalStorageDirectory(), MODELS_DIR_NAME)

    /** Public shared embedding bundle dir, mirroring [embeddingDir]'s layout. */
    fun publicEmbeddingDir(id: String): File = File(publicModelsRoot(), "embeddings/$id")

    /** True if this flavour can ask for, and has been granted, All Files Access. */
    fun hasAllFilesAccess(): Boolean =
        BuildConfig.ALL_FILES_ACCESS_ENABLED &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager())

    /** Every directory worth checking for an already-present chat model, app-private first. */
    fun chatSearchDirs(): List<File> =
        listOfNotNull(chatModelsDir(), publicModelsRoot().takeIf { hasAllFilesAccess() })

    /** Every directory worth checking for an already-present embedding bundle. */
    fun embeddingSearchDirs(id: String): List<File> =
        listOfNotNull(embeddingDir(id), publicEmbeddingDir(id).takeIf { hasAllFilesAccess() })

    /**
     * Where to actually load a chat GGUF from — the first of [chatSearchDirs]
     * that has it, so a model sitting in the public folder loads exactly like
     * one this app downloaded itself. Falls back to the app-private path
     * (even if nothing is there yet) so a fresh download still has somewhere
     * to land.
     */
    fun resolvedChatModelFile(fileName: String): File {
        for (dir in chatSearchDirs()) {
            val candidate = File(dir, fileName)
            if (candidate.isFile) return candidate
        }
        return chatModelFile(fileName)
    }

    /** Same resolution as [resolvedChatModelFile], for the embedding bundle's directory. */
    fun resolvedEmbeddingDir(id: String, requiredFileNames: List<String>): File {
        for (dir in embeddingSearchDirs(id)) {
            if (requiredFileNames.all { File(dir, it).isFile }) return dir
        }
        return embeddingDir(id)
    }

    /** Usable free bytes on the filesystem backing [dir]. */
    fun usableSpaceBytes(dir: File): Long =
        runCatching { (dir.takeIf { it.exists() } ?: dir.parentFile)?.usableSpace ?: 0L }.getOrDefault(0L)

    companion object {
        /** Folder name under the app's external files dir (and, when reachable, shared storage). */
        const val MODELS_DIR_NAME = "AI Models"
    }
}
