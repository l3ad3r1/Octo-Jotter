package com.l3ad3r1.octojotter.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.l3ad3r1.octojotter.data.local.AppDatabase
import com.l3ad3r1.octojotter.data.remote.RetrofitClient
import com.l3ad3r1.octojotter.data.remote.TokenManager
import com.l3ad3r1.octojotter.data.repository.NoteRepository

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val noteDao = database.noteDao()
        val githubApiService = RetrofitClient.githubApiService
        val tokenManager = TokenManager(applicationContext)
        val repository = NoteRepository(noteDao, githubApiService, tokenManager)

        val result = repository.pushToGithub()
        return if (result.isSuccess) {
            Result.success()
        } else {
            val message = result.exceptionOrNull()?.message ?: ""
            if (message.contains("No GitHub token", ignoreCase = true) || message.contains("401", ignoreCase = true)) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }
}
