package com.l3ad3r1.octojotter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY name COLLATE NOCASE ASC")
    fun getAllFlow(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: TemplateEntity)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteById(id: String)
}
