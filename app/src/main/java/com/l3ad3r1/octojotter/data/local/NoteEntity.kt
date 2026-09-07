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
    val sha: String? = null,
    val deletedAt: Long? = null,
    // Set when the user empties the Trash: this note is queued for permanent
    // deletion *including* its remote copy. Being in the Trash alone never sets
    // it — trashing stays local and reversible until the trash is emptied.
    // NoteRepository.processPendingRemoteDeletes() drains the queue and hard-
    // deletes each row once its Gist/repo file is actually gone, so a delete
    // made offline still reaches GitHub on the next sync instead of the note
    // reappearing on the next pull.
    val pendingRemoteDelete: Boolean = false,
    val locked: Boolean = false,
    val encrypted: Boolean = false,
    val encryptionVersion: Int = 0,
    val remoteUpdatedAt: String? = null,
    val lastSyncedContentHash: String? = null,
    val conflictState: String? = null,
    val conflictedRemoteContent: String? = null,
    val conflictedRemoteModifiedAt: Long? = null,
    // --- v12 additions ---
    // One of NoteColor's ids (see ui/NoteColor.kt), or null for the default
    // surface color. A Google Keep-style at-a-glance label, purely visual.
    val color: String? = null,
    // Set only on the one note-per-date created by the Daily Notes plugin, so
    // "today's note" can be found without guessing at a title format.
    val isDailyNote: Boolean = false,
    // Epoch millis for an optional note-level reminder (Task Reminders
    // plugin). Null = no reminder scheduled.
    val reminderAt: Long? = null,
    // --- v13 addition ---
    // The filename this note currently occupies inside its Gist. The Gist API
    // renames a file only when the request is keyed by its *old* filename with
    // a new `filename` value; keying by the new name silently adds a second
    // file instead, and the next pull can then restore the stale one. Knowing
    // the remote name is what makes a rename a rename. Null for repo-backed
    // notes (they use `path`) and for notes not yet pushed.
    val remoteFilename: String? = null
) {
    val folderPath: List<String>
        get() = folder
            ?.split("/")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: if (title.contains("__")) {
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
