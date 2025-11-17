package com.example.sonicwavev4.data.custompreset

import com.example.sonicwavev4.data.custompreset.model.CreateCustomPresetRequest
import com.example.sonicwavev4.data.custompreset.model.CustomPreset
import com.example.sonicwavev4.data.custompreset.model.ImportResult
import com.example.sonicwavev4.data.custompreset.model.UpdateCustomPresetRequest
import kotlinx.coroutines.flow.Flow

interface CustomPresetRepository {
    fun customPresets(customerId: Long?): Flow<List<CustomPreset>>

    suspend fun create(request: CreateCustomPresetRequest): String
    suspend fun update(request: UpdateCustomPresetRequest)
    suspend fun delete(id: String)
    suspend fun reorderPresets(newOrderIds: List<String>)
    suspend fun getPresetById(id: String): CustomPreset?

    suspend fun exportAll(): List<CustomPreset>
    suspend fun importAll(presets: List<CustomPreset>, overwrite: Boolean): ImportResult
}
