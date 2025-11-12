package com.example.sonicwavev4.data.custompreset.db

import androidx.room.TypeConverter
import com.example.sonicwavev4.data.custompreset.model.SyncState

class CustomPresetConverters {
    @TypeConverter
    fun fromSyncState(value: SyncState?): String? = value?.name

    @TypeConverter
    fun toSyncState(value: String?): SyncState? = value?.let { SyncState.valueOf(it) }
}
