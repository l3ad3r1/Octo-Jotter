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

        // Deliberately no default repositories.
        //
        // This used to seed two of the maintainer's own private vaults
        // ("l3ad3r1/Dronehire-second-brain", "l3ad3r1/Cane-Theory-second-brain").
        // Octo Jotter is a public app: every install listed one person's private
        // repository names in its Repository Sync screen, before any token was
        // entered and to users who had nothing to do with them. It also made the
        // first sync attempt of a fresh install target a repo the user could not
        // read, so the failure looked like a bug in sync.
        //
        // A repository list is per-user data. It starts empty and the user adds
        // their own.
    }

    val repositories: Flow<Set<String>> = context.repoDataStore.data
        .map { preferences -> preferences[REPOS_KEY].orEmpty() }

    /** Null until the user has added and chosen a repository of their own. */
    val selectedRepository: Flow<String?> = context.repoDataStore.data
        .map { preferences ->
            // Fall back to whatever they have added rather than to a constant,
            // so a single stored repo is still the active one.
            preferences[SELECTED_REPO_KEY] ?: preferences[REPOS_KEY]?.firstOrNull()
        }

    suspend fun addRepository(repo: String) {
        val cleaned = repo.trim().trim('/')
        if (cleaned.isBlank() || !cleaned.contains("/")) return
        context.repoDataStore.edit { preferences ->
            val current = preferences[REPOS_KEY].orEmpty()
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
            val current = preferences[REPOS_KEY].orEmpty()
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
