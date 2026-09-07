package com.l3ad3r1.octojotter.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.l3ad3r1.octojotter.data.local.AppDatabase
import com.l3ad3r1.octojotter.data.local.RepoPreferences
import com.l3ad3r1.octojotter.data.remote.RetrofitClient
import com.l3ad3r1.octojotter.data.remote.TokenManager
import com.l3ad3r1.octojotter.data.repository.NoteRepository
import com.l3ad3r1.octojotter.plugin.FeaturePluginIds
import kotlinx.coroutines.flow.first

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        // GitHub Sync is a built-in feature plugin now — disabling it (Settings
        // or Community Plugins) has to actually stop this background work, not
        // just hide the Settings section, or "off" would be cosmetic.
        val syncEnabled = database.pluginDao().getPluginById(FeaturePluginIds.GITHUB_SYNC)?.enabled ?: true
        if (!syncEnabled) return Result.success()
        val noteDao = database.noteDao()
        val githubApiService = RetrofitClient.githubApiService
        val tokenManager = TokenManager(applicationContext)
        val repository = NoteRepository(noteDao, githubApiService, tokenManager)

        val failures = mutableListOf<kotlin.Result<Unit>>()

        // Deletions first: a note whose Gist still exists comes straight back on
        // the pull below, so the queue has to drain before anything is fetched.
        failures += repository.processPendingRemoteDeletes()

        failures += repository.pullFromGithub()
        failures += repository.pushToGithub()

        // Repository-backed notes are edited through the same editor and marked
        // needsSync the same way, so background sync has to cover them too —
        // otherwise every triggerBackgroundSync() after a repo-note edit was a
        // no-op and those edits sat unsynced until someone pressed "Sync now".
        val repoPath = runCatching { RepoPreferences(applicationContext).selectedRepository.first() }
            .getOrNull()
        if (!repoPath.isNullOrBlank()) {
            failures += repository.pullFromRepository(repoPath)
            failures += repository.pushToRepository(repoPath)
        }

        val firstFailure = failures.firstOrNull { it.isFailure }?.exceptionOrNull()
            ?: return Result.success()

        val message = firstFailure.message ?: ""
        // A missing or rejected token will not fix itself on a retry.
        return if (message.contains("No GitHub token", ignoreCase = true) ||
            message.contains("401", ignoreCase = true)
        ) {
            Result.failure()
        } else {
            Result.retry()
        }
    }
}
