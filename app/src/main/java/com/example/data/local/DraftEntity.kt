package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val noteId: Int,
    val title: String,
    val content: String,
    val lastSaved: Long = System.currentTimeMillis()
)
