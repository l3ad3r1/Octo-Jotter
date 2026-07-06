package com.l3ad3r1.octojotter.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val name: String
)

@Entity(
    tableName = "note_tag_cross_ref",
    primaryKeys = ["noteId", "tagName"]
)
data class NoteTagCrossRef(
    val noteId: Int,
    val tagName: String
)

data class NoteWithTags(
    @Embedded val note: NoteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "name",
        associateBy = Junction(
            value = NoteTagCrossRef::class,
            parentColumn = "noteId",
            entityColumn = "tagName"
        )
    )
    val tags: List<TagEntity>
)
