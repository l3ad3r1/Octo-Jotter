package com.l3ad3r1.octojotter.data.local

data class DatabaseBackup(
    val exportTime: Long,
    val notes: List<NoteEntity>,
    val drafts: List<DraftEntity>
)
