package com.l3ad3r1.octojotter.ai.index

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.l3ad3r1.octojotter.ai.AiContainer

/**
 * Background full-index pass (spec §4). Deferred, incremental, and safe to run
 * often — unchanged notes are skipped. Does nothing if the device can't run AI
 * or the embedder isn't ready yet.
 */
class NoteIndexingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ai = AiContainer.get(applicationContext)
        if (!ai.capability.supportsSemanticSearch) return Result.success()
        return try {
            ai.indexer().indexAll()
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK = "octojotter-note-indexing"

        /** Enqueue a single deferred index pass, coalescing duplicates. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<NoteIndexingWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
        }
    }
}
