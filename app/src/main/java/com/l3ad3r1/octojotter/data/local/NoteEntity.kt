package com.l3ad3r1.octojotter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val gistId: String? = null,
    val title: String,
    val content: String,
    val lastModifiedLocally: Long = System.currentTimeMillis(),
    val needsSync: Boolean = false,
    val pinned: Boolean = false,
    val tags: List<String> = emptyList(),
    val folder: String? = null,
    // --- Repository sync fields (v7) ---
    // When set, this note originates from a GitHub repository ("owner/repo")
    // rather than a Gist. `path` is the file path within that repo and `sha`
    // is the blob SHA of the last-synced version (used for conflict-safe PUTs).
    val repository: String? = null,
    val path: String? = null,
    val sha: String? = null
) {
    val folderPath: List<String>
        get() = if (title.contains("__")) {
            title.split("__").dropLast(1)
        } else {
            emptyList()
        }

    // Full nesting location for the folder tree / drawer: the repository name
    // (for repo-backed notes) followed by the in-repo folders. Non-repo notes
    // just use their folders. This is what makes repos nest into folders.
    val locationPath: List<String>
        get() = if (!repository.isNullOrBlank()) {
            listOf(repository.substringAfterLast('/')) + folderPath
        } else {
            folderPath
        }

    val displayTitle: String
        get() = if (title.contains("__")) {
            title.split("__").last()
        } else {
            title
        }
}
