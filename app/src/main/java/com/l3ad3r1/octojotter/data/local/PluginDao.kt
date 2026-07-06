package com.l3ad3r1.octojotter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginDao {
    @Query("SELECT * FROM plugins ORDER BY name COLLATE NOCASE ASC")
    fun getAllPluginsFlow(): Flow<List<PluginEntity>>

    @Query("SELECT * FROM plugins WHERE id = :id LIMIT 1")
    suspend fun getPluginById(id: String): PluginEntity?

    // Enabled plugins of a type; used to resolve the active theme (LIMIT 1).
    @Query("SELECT * FROM plugins WHERE type = :type AND enabled = 1 ORDER BY installedAt DESC")
    fun getEnabledByTypeFlow(type: String): Flow<List<PluginEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plugin: PluginEntity)

    @Query("UPDATE plugins SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    // Disable every plugin of a type except [keepId] — enforces one active theme.
    @Query("UPDATE plugins SET enabled = 0 WHERE type = :type AND id != :keepId")
    suspend fun disableOthersOfType(type: String, keepId: String)

    @Query("DELETE FROM plugins WHERE id = :id")
    suspend fun deleteById(id: String)
}
