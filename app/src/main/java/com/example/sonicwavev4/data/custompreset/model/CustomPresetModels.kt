package com.example.sonicwavev4.data.custompreset.model

import java.util.UUID

/**
 * Domain 层的自设模式数据结构。
 * 之所以单独定义而不是直接使用 Room Entity，是为了方便未来接入云同步或导出导入。
 */
data class CustomPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<CustomPresetStep>,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long?,
    val syncState: SyncState
)

data class CustomPresetStep(
    val id: String = UUID.randomUUID().toString(),
    val frequencyHz: Int,
    val intensity01V: Int,
    val durationSec: Int,
    val order: Int
)

enum class SyncState {
    LOCAL_ONLY,
    SYNCED,
    DIRTY
}
