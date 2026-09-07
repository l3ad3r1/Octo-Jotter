package com.l3ad3r1.octojotter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A user-authored note template (Templates plugin). [content] may contain the
 * variables `{{date}}`, `{{time}}`, `{{title}}`, and `{{cursor}}` — expanded by
 * `TemplateEngine` at note-creation time, the same substitution style
 * Obsidian's own templates use.
 */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
