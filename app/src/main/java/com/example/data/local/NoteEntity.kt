package com.example.data.local

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
    val folder: String? = null
) {
    val folderPath: List<String>
        get() = if (title.contains("__")) {
            title.split("__").dropLast(1)
        } else {
            emptyList()
        }

    val displayTitle: String
        get() = if (title.contains("__")) {
            title.split("__").last()
        } else {
            title
        }
}
