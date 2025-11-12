package com.example.sonicwavev4.data.custompreset

import android.content.Context
import com.example.sonicwavev4.data.custompreset.db.CustomPresetDao
import com.example.sonicwavev4.data.custompreset.db.CustomPresetDatabase
import com.example.sonicwavev4.data.custompreset.db.CustomPresetEntity
import com.example.sonicwavev4.data.custompreset.model.CreateCustomPresetRequest
import com.example.sonicwavev4.data.custompreset.model.CustomPreset
import com.example.sonicwavev4.data.custompreset.model.CustomPresetStep
import com.example.sonicwavev4.data.custompreset.model.ImportResult
import com.example.sonicwavev4.data.custompreset.model.SyncState
import com.example.sonicwavev4.data.custompreset.model.UpdateCustomPresetRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 自设模式仓库：负责 Room 读写和步骤 JSON 序列化。
 * 由于产品后续要同步云端，这里保留了 sortOrder/syncState 等字段，便于未来拓展。
 */
class CustomPresetRepositoryImpl private constructor(
    private val dao: CustomPresetDao,
    private val gson: Gson,
    private val dispatcher: CoroutineDispatcher
) : CustomPresetRepository {

    override val customPresets: Flow<List<CustomPreset>> =
        dao.observeAll()
            .map { entities -> entities.map { it.toDomain(gson) } }
            .flowOn(dispatcher)

    override suspend fun create(request: CreateCustomPresetRequest): String = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val entity = CustomPresetEntity(
            id = UUID.randomUUID().toString(),
            name = request.name.trim(),
            stepsJson = gson.toJson(request.steps.normalizeOrder(), STEP_LIST_TYPE),
            sortOrder = (dao.maxSortOrder() ?: 0) + 1,
            createdAt = now,
            updatedAt = now,
            lastSyncedAt = null,
            syncState = SyncState.LOCAL_ONLY
        )
        dao.upsert(entity)
        entity.id
    }

    override suspend fun update(request: UpdateCustomPresetRequest) = withContext(dispatcher) {
        val existing = dao.findById(request.id) ?: return@withContext
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            name = request.name.trim(),
            stepsJson = gson.toJson(request.steps.normalizeOrder(), STEP_LIST_TYPE),
            updatedAt = now,
            syncState = SyncState.DIRTY
        )
        dao.upsert(updated)
    }

    override suspend fun delete(id: String) = withContext(dispatcher) {
        val existing = dao.findById(id) ?: return@withContext
        dao.delete(existing)
    }

    override suspend fun getPresetById(id: String): CustomPreset? = withContext(dispatcher) {
        dao.findById(id)?.toDomain(gson)
    }

    override suspend fun reorderPresets(newOrderIds: List<String>) = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        newOrderIds.forEachIndexed { index, presetId ->
            dao.updateSortOrder(presetId, index, now)
        }
    }

    override suspend fun exportAll(): List<CustomPreset> = withContext(dispatcher) {
        dao.listAll().map { it.toDomain(gson) }
    }

    override suspend fun importAll(
        presets: List<CustomPreset>,
        overwrite: Boolean
    ): ImportResult = withContext(dispatcher) {
        var imported = 0
        var skipped = 0
        presets.forEach { preset ->
            val existing = dao.findById(preset.id)
            if (existing != null && !overwrite) {
                skipped++
                return@forEach
            }
            val now = System.currentTimeMillis()
            val entity = CustomPresetEntity(
                id = preset.id.ifBlank { UUID.randomUUID().toString() },
                name = preset.name,
                stepsJson = gson.toJson(preset.steps.normalizeOrder(), STEP_LIST_TYPE),
                sortOrder = preset.sortOrder,
                createdAt = preset.createdAt,
                updatedAt = now,
                lastSyncedAt = preset.lastSyncedAt,
                syncState = preset.syncState
            )
            dao.upsert(entity)
            imported++
        }
        ImportResult(imported = imported, skipped = skipped)
    }

    private fun List<CustomPresetStep>.normalizeOrder(): List<CustomPresetStep> =
        this.sortedBy { it.order }.mapIndexed { index, step ->
            step.copy(order = index)
        }

    companion object {
        @Volatile
        private var instance: CustomPresetRepository? = null

        fun getInstance(context: Context): CustomPresetRepository {
            return instance ?: synchronized(this) {
                instance ?: buildRepository(context.applicationContext).also { instance = it }
            }
        }

        private fun buildRepository(context: Context): CustomPresetRepository {
            val database = CustomPresetDatabase.getInstance(context)
            return CustomPresetRepositoryImpl(
                dao = database.customPresetDao(),
                gson = Gson(),
                dispatcher = Dispatchers.IO
            )
        }
    }
}

private val STEP_LIST_TYPE = object : TypeToken<List<CustomPresetStep>>() {}.type

private fun CustomPresetEntity.toDomain(gson: Gson): CustomPreset =
    CustomPreset(
        id = id,
        name = name,
        steps = gson.fromJson(stepsJson, STEP_LIST_TYPE),
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastSyncedAt = lastSyncedAt,
        syncState = syncState
    )
