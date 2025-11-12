package com.example.sonicwavev4.data.custompreset.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomPresetDao {
    @Query("SELECT * FROM custom_presets ORDER BY sort_order ASC, created_at ASC")
    fun observeAll(): Flow<List<CustomPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CustomPresetEntity)

    @Delete
    suspend fun delete(entity: CustomPresetEntity)

    @Query("SELECT * FROM custom_presets WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CustomPresetEntity?

    @Query("UPDATE custom_presets SET sort_order = :sortOrder, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAt: Long)

    @Query("SELECT MAX(sort_order) FROM custom_presets")
    suspend fun maxSortOrder(): Int?

    @Query("SELECT * FROM custom_presets ORDER BY sort_order ASC, created_at ASC")
    suspend fun listAll(): List<CustomPresetEntity>
}
