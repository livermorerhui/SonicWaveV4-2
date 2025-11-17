package com.example.sonicwavev4.ui.persetmode.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.data.custompreset.CustomPresetRepository
import com.example.sonicwavev4.data.custompreset.CustomPresetConstraints
import com.example.sonicwavev4.data.custompreset.model.CreateCustomPresetRequest
import com.example.sonicwavev4.data.custompreset.model.CustomPresetStep
import com.example.sonicwavev4.data.custompreset.model.UpdateCustomPresetRequest
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val presetId: String? = null,
    val name: String = "",
    val frequencyInput: String = "",
    val intensityInput: String = "",
    val durationInput: String = "",
    val steps: List<CustomPresetStep> = emptyList(),
    val editingStepId: String? = null,
    val isSaving: Boolean = false,
    val canSave: Boolean = false
) {
    val isEditingExisting: Boolean get() = presetId != null
    val isEditingStep: Boolean get() = editingStepId != null
}

sealed class EditorEvent {
    data class ShowMessage(val message: String) : EditorEvent()
    data class Saved(val presetId: String) : EditorEvent()
}

/**
 * 编辑界面的业务核心：管理表单输入、步骤增删改/排序，并最终落地到仓库。
 * 所有步骤顺序统一通过 normalizeOrder，避免执行过程中出现跳号。
 */
class CustomPresetEditorViewModel(
    private val repository: CustomPresetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    fun startNewPreset() {
        _uiState.value = EditorUiState()
    }

    fun loadPreset(presetId: String) {
        viewModelScope.launch {
            val preset = repository.getPresetById(presetId)
            if (preset == null) {
                emitMessage("未找到该自设模式，保存后将创建新的模式")
                _uiState.update { EditorUiState(presetId = null) }
            } else {
                _uiState.update {
                    EditorUiState(
                        presetId = preset.id,
                        name = preset.name,
                        steps = preset.steps.sortedBy { it.order }
                    ).recomputeCanSave()
                }
            }
        }
    }

    fun setName(value: String) {
        _uiState.update { it.copy(name = value).recomputeCanSave() }
    }

    fun setFrequencyInput(value: String) {
        _uiState.update { it.copy(frequencyInput = value) }
    }

    fun setIntensityInput(value: String) {
        _uiState.update { it.copy(intensityInput = value) }
    }

    fun setDurationInput(value: String) {
        _uiState.update { it.copy(durationInput = value) }
    }

    fun startEditingStep(stepId: String) {
        val current = _uiState.value
        val step = current.steps.firstOrNull { it.id == stepId } ?: return
        _uiState.update {
            it.copy(
                editingStepId = stepId,
                frequencyInput = step.frequencyHz.toString(),
                intensityInput = step.intensity01V.toString(),
                durationInput = step.durationSec.toString()
            )
        }
    }

    fun cancelStepEditing() {
        _uiState.update {
            it.copy(
                editingStepId = null,
                frequencyInput = "",
                intensityInput = "",
                durationInput = ""
            )
        }
    }

    fun addOrUpdateStep() {
        val current = _uiState.value
        val frequency = current.frequencyInput.toIntOrNull()
        val intensity = current.intensityInput.toIntOrNull()
        val duration = current.durationInput.toIntOrNull()
        if (frequency == null || intensity == null || duration == null) {
            emitMessage("请正确输入频率、强度和时间")
            return
        }
        val sanitized = CustomPresetStep(
            id = current.editingStepId ?: UUID.randomUUID().toString(),
            frequencyHz = CustomPresetConstraints.clampFrequency(frequency),
            intensity01V = CustomPresetConstraints.clampIntensity(intensity),
            durationSec = CustomPresetConstraints.clampDuration(duration),
            order = current.steps.size
        )
        if (current.editingStepId == null) {
            _uiState.update {
                it.copy(
                    steps = (it.steps + sanitized).normalizeOrder(),
                    frequencyInput = "",
                    intensityInput = "",
                    durationInput = ""
                ).recomputeCanSave()
            }
        } else {
            val updatedSteps = current.steps.map { step ->
                if (step.id == sanitized.id) sanitized.copy(order = step.order) else step
            }
            _uiState.update {
                it.copy(
                    steps = updatedSteps.normalizeOrder(),
                    editingStepId = null,
                    frequencyInput = "",
                    intensityInput = "",
                    durationInput = ""
                ).recomputeCanSave()
            }
        }
    }

    fun deleteStep(stepId: String) {
        _uiState.update {
            it.copy(steps = it.steps.filterNot { step -> step.id == stepId }.normalizeOrder())
                .recomputeCanSave()
        }
    }

    fun moveStepUp(stepId: String) {
        reorderStep(stepId, -1)
    }

    fun moveStepDown(stepId: String) {
        reorderStep(stepId, 1)
    }

    private fun reorderStep(stepId: String, delta: Int) {
        val current = _uiState.value
        val index = current.steps.indexOfFirst { it.id == stepId }
        if (index == -1) return
        val targetIndex = index + delta
        if (targetIndex < 0 || targetIndex >= current.steps.size) return
        val mutable = current.steps.toMutableList()
        val step = mutable.removeAt(index)
        mutable.add(targetIndex, step)
        _uiState.update { it.copy(steps = mutable.normalizeOrder()).recomputeCanSave() }
    }

    fun savePreset() {
        val current = _uiState.value
        if (!current.canSave || current.isSaving) {
            if (!current.canSave) {
                emitMessage("请填写名称并添加至少一个步骤")
            }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val steps = current.steps.normalizeOrder()
                val presetId = if (current.presetId == null) {
                    repository.create(
                        CreateCustomPresetRequest(
                            name = current.name.trim(),
                            steps = steps
                        )
                    )
                } else {
                    repository.update(
                        UpdateCustomPresetRequest(
                            id = current.presetId,
                            name = current.name.trim(),
                            steps = steps
                        )
                    )
                    current.presetId
                }
                _events.emit(EditorEvent.Saved(presetId))
                val refreshed = repository.getPresetById(presetId)
                if (refreshed != null) {
                    _uiState.update {
                        it.copy(
                            presetId = presetId,
                            name = refreshed.name,
                            steps = refreshed.steps.sortedBy { step -> step.order },
                            isSaving = false,
                            editingStepId = null,
                            frequencyInput = "",
                            intensityInput = "",
                            durationInput = ""
                        ).recomputeCanSave()
                    }
                } else {
                    _uiState.update { it.copy(presetId = presetId, isSaving = false).recomputeCanSave() }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                emitMessage(e.message ?: "保存自设模式失败")
            }
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(EditorEvent.ShowMessage(message))
        }
    }

    private fun EditorUiState.recomputeCanSave(): EditorUiState =
        copy(canSave = name.isNotBlank() && steps.isNotEmpty())
}

private fun List<CustomPresetStep>.normalizeOrder(): List<CustomPresetStep> =
    mapIndexed { index, step -> step.copy(order = index) }
