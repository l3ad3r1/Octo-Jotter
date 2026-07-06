package com.l3ad3r1.octojotter.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.repoDataStore: DataStore<Preferences> by preferencesDataStore(name = "repo_settings")

/**
 * Persists the set of GitHub repositories ("owner/repo") the user syncs notes
 * with, plus which one is currently selected. Repos are private knowledge
 * bases, so the saved PAT must carry the `repo` scope.
 */
class RepoPreferences(private val context: Context) {
    companion object {
        val REPOS_KEY = stringSetPreferencesKey("repositories_list")
        val SELECTED_REPO_KEY = stringPreferencesKey("selected_repository")

        // Owner is l3ad3r1 (the second-brain vaults live there — the earlier
        // renjacob10000/* defaults 404'd and were the root cause of pull failures).
        val DEFAULT_REPOS = setOf(
            "l3ad3r1/Dronehire-second-brain",
            "l3ad3r1/Cane-Theory-second-brain"
        )
    }

    val repositories: Flow<Set<String>> = context.repoDataStore.data
        .map { preferences ->
            val stored = preferences[REPOS_KEY]
            if (stored.isNullOrEmpty()) DEFAULT_REPOS else stored
        }

    val selectedRepository: Flow<String?> = context.repoDataStore.data
        .map { preferences ->
            preferences[SELECTED_REPO_KEY] ?: DEFAULT_REPOS.first()
        }

    suspend fun addRepository(repo: String) {
        val cleaned = repo.trim().trim('/')
        if (cleaned.isBlank() || !cleaned.contains("/")) return
        context.repoDataStore.edit { preferences ->
            val current = preferences[REPOS_KEY] ?: DEFAULT_REPOS
            preferences[REPOS_KEY] = current + cleaned
        }
    }

    suspend fun setSelectedRepository(repo: String?) {
        context.repoDataStore.edit { preferences ->
            if (repo == null) {
                preferences.remove(SELECTED_REPO_KEY)
            } else {
                preferences[SELECTED_REPO_KEY] = repo.trim()
            }
        }
    }

    suspend fun deleteRepository(repo: String) {
        context.repoDataStore.edit { preferences ->
            val current = preferences[REPOS_KEY] ?: DEFAULT_REPOS
            val updated = current - repo
            preferences[REPOS_KEY] = updated
            // If the active repo was removed, fall back to another one (or clear).
            if ((preferences[SELECTED_REPO_KEY] ?: "") == repo) {
                val next = updated.firstOrNull()
                if (next == null) preferences.remove(SELECTED_REPO_KEY)
                else preferences[SELECTED_REPO_KEY] = next
            }
        }
    }
}
