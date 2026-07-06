package com.example.data.local

data class DatabaseBackup(
    val exportTime: Long,
    val notes: List<NoteEntity>,
    val drafts: List<DraftEntity>
)
