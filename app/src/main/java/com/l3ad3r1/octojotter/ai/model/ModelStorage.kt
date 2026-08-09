package com.l3ad3r1.octojotter.ai.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * Resolves where on-device model files live, deliberately sharing Hermes Agent's
 * layout so a model downloaded by either app is reused by the other.
 *
 * Hermes stores GGUF chat models in the public folder
 * `<external-storage>/AI Models/` (see Hermes `ModelCatalog.DEFAULT_DIR_NAME`).
 * When Octo Jotter has All-Files-Access, it points at the *same* folder and the
 * *same* filenames, so a Hermes download is detected as already-present — no
 * second multi-GB download.
 *
 * Without that permission we fall back to Octo's own external files dir
 * (`Android/data/<pkg>/files/AI Models`), which needs no permission but is
 * app-private and therefore NOT shared with Hermes.
 */
class ModelStorage(private val context: Context) {

    /** Hermes-compatible shared root: `<external-storage>/AI Models`. */
    fun sharedRoot(): File = File(Environment.getExternalStorageDirectory(), SHARED_DIR_NAME)

    /** App-private fallback that needs no storage permission. */
    fun privateRoot(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, SHARED_DIR_NAME)

    /** True when we can read/write arbitrary shared storage (All-Files-Access). */
    fun hasSharedAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /** True once we are actually using the shared (Hermes) location. */
    val usingSharedStorage: Boolean get() = hasSharedAccess()

    /** Directory GGUF chat models live in — the Hermes folder when shared. */
    fun chatModelsDir(): File =
        (if (hasSharedAccess()) sharedRoot() else privateRoot()).apply { mkdirs() }

    /** Directory for an embedding model bundle (model + vocab), also shareable. */
    fun embeddingDir(id: String): File =
        File(chatModelsDir(), "embeddings/$id").apply { mkdirs() }

    /** Full path a chat model file resolves to (Hermes-identical filename). */
    fun chatModelFile(fileName: String): File = File(chatModelsDir(), fileName)

    /** Usable free bytes on the filesystem backing [dir]. */
    fun usableSpaceBytes(dir: File): Long =
        runCatching { (dir.takeIf { it.exists() } ?: dir.parentFile)?.usableSpace ?: 0L }.getOrDefault(0L)

    companion object {
        /** Matches Hermes `ModelCatalog.DEFAULT_DIR_NAME`. Do not rename — it is the
         *  cross-app contract that makes model sharing work. */
        const val SHARED_DIR_NAME = "AI Models"

        /** Intent that opens the system "All files access" grant screen for this app. */
        fun allFilesAccessIntent(context: Context): Intent =
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
    }
}
