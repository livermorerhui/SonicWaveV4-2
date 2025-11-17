package com.example.sonicwavev4.data.custompreset.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.sonicwavev4.data.custompreset.model.SyncState
import java.util.UUID

@Entity(tableName = "custom_presets")
data class CustomPresetEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "customer_id")
    val customerId: Long?,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "steps_json")
    val stepsJson: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long?,
    @ColumnInfo(name = "sync_state")
    val syncState: SyncState
)
