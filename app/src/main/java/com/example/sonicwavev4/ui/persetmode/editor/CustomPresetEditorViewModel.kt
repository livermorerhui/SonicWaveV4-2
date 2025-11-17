package com.example.sonicwavev4.ui.persetmode.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.data.custompreset.CustomPresetRepository
import com.example.sonicwavev4.data.custompreset.CustomPresetConstraints
import com.example.sonicwavev4.data.custompreset.model.CreateCustomPresetRequest
import com.example.sonicwavev4.data.custompreset.model.CustomPresetStep
import com.example.sonicwavev4.data.custompreset.model.UpdateCustomPresetRequest
import com.example.sonicwavev4.data.home.HomeHardwareRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val NEW_STEP_ID = "__new_step__"

data class EditorUiState(
    val presetId: String? = null,
    val name: String = "",
    val originalName: String = "",
    val frequencyInput: String = "",
    val intensityInput: String = "",
    val durationInput: String = "",
    val steps: List<CustomPresetStep> = emptyList(),
    val originalSteps: List<CustomPresetStep> = emptyList(),
    val editingStepId: String? = null,
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
    val playingStepIndex: Int? = null
) {
    val isEditingExisting: Boolean get() = presetId != null
}

fun EditorUiState.hasUnsavedChanges(): Boolean {
    val hasStepEditing = editingStepId != null
    val nameChanged = name != originalName
    val stepsChanged = steps.normalizeOrder() != originalSteps.normalizeOrder()
    return hasStepEditing || nameChanged || stepsChanged
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
    private val repository: CustomPresetRepository,
    private val customerId: Long?,
    private val hardwareRepository: HomeHardwareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    private val previewPlayer = StepPreviewPlayer()
    private var previewUsingHardware = false

    fun startNewPreset() {
        _uiState.value = EditorUiState(originalName = "", originalSteps = emptyList())
    }

    fun loadPreset(presetId: String) {
        viewModelScope.launch {
            val preset = repository.getPresetById(presetId)
            if (preset == null) {
                emitMessage("未找到该自设模式，保存后将创建新的模式")
                _uiState.update { EditorUiState(presetId = null) }
            } else {
                val ordered = preset.steps.sortedBy { it.order }
                _uiState.update {
                    EditorUiState(
                        presetId = preset.id,
                        name = preset.name,
                        originalName = preset.name,
                        steps = ordered,
                        originalSteps = ordered
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

    fun addStepInline() {
        val current = _uiState.value
        val newStep = CustomPresetStep(
            id = UUID.randomUUID().toString(),
            frequencyHz = 0,
            intensity01V = 0,
            durationSec = 1,
            order = current.steps.size
        )
        _uiState.update { it.copy(steps = (it.steps + newStep).normalizeOrder()).recomputeCanSave() }
    }

    fun deleteStep(stepId: String) {
        _uiState.update {
            it.copy(steps = it.steps.filterNot { step -> step.id == stepId }.normalizeOrder())
                .recomputeCanSave()
        }
    }

    fun moveStepUp(index: Int) {
        onStepReordered(index, index - 1)
    }

    fun moveStepDown(index: Int) {
        onStepReordered(index, index + 1)
    }

    fun updateStep(index: Int, newStep: CustomPresetStep) {
        val current = _uiState.value
        if (index !in current.steps.indices) return
        val updated = current.steps.toMutableList()
        updated[index] = newStep.copy(
            frequencyHz = CustomPresetConstraints.clampFrequency(newStep.frequencyHz),
            intensity01V = CustomPresetConstraints.clampIntensity(newStep.intensity01V),
            durationSec = CustomPresetConstraints.clampDuration(newStep.durationSec),
            order = current.steps[index].order
        )
        _uiState.update { it.copy(steps = updated.normalizeOrder()).recomputeCanSave() }
        val playingIdx = _uiState.value.playingStepIndex
        if (playingIdx == index) {
            previewPlayer.updateFrequency(updated[index].frequencyHz)
            previewPlayer.updateIntensity(updated[index].intensity01V)
            viewModelScope.launch {
                if (previewUsingHardware) {
                    hardwareRepository.applyFrequency(updated[index].frequencyHz)
                    hardwareRepository.applyIntensity(updated[index].intensity01V)
                } else {
                    hardwareRepository.playStandaloneTone(
                        updated[index].frequencyHz,
                        updated[index].intensity01V
                    )
                }
            }
        }
    }

    fun updateStepFrequency(index: Int, value: Int) {
        val current = _uiState.value
        if (index !in current.steps.indices) return
        val step = current.steps[index]
        updateStep(index, step.copy(frequencyHz = value))
    }

    fun updateStepIntensity(index: Int, value: Int) {
        val current = _uiState.value
        if (index !in current.steps.indices) return
        val step = current.steps[index]
        updateStep(index, step.copy(intensity01V = value))
    }

    fun updateStepDuration(index: Int, value: Int) {
        val current = _uiState.value
        if (index !in current.steps.indices) return
        val step = current.steps[index]
        updateStep(index, step.copy(durationSec = value))
    }

    fun onPlayStepClicked(index: Int) {
        val state = _uiState.value
        val currentPlaying = state.playingStepIndex
        if (currentPlaying == index) {
            stopPlaybackInternal()
            _uiState.update { it.copy(playingStepIndex = null) }
            return
        }
        if (currentPlaying != null) {
            stopPlaybackInternal()
        }
        if (index !in state.steps.indices) return
        val step = state.steps[index]
        startPlaybackInternal(step)
        _uiState.update { it.copy(playingStepIndex = index) }
    }

    fun stopCurrentStepPlayback() {
        val state = _uiState.value
        if (state.playingStepIndex != null) {
            stopPlaybackInternal()
            _uiState.update { it.copy(playingStepIndex = null) }
        }
    }

    private fun startPlaybackInternal(step: CustomPresetStep) {
        previewPlayer.start(step)
        viewModelScope.launch {
            hardwareRepository.start()
            val success = hardwareRepository.startOutput(
                targetFrequency = step.frequencyHz,
                targetIntensity = step.intensity01V,
                playTone = true
            )
            previewUsingHardware = success
            if (!success) {
                hardwareRepository.playStandaloneTone(step.frequencyHz, step.intensity01V)
            }
        }
    }

    private fun stopPlaybackInternal() {
        previewPlayer.stop()
        viewModelScope.launch {
            if (previewUsingHardware) {
                hardwareRepository.stopOutput()
            } else {
                hardwareRepository.stopStandaloneTone()
            }
            previewUsingHardware = false
        }
    }

    fun onStepReordered(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value
        if (fromIndex !in current.steps.indices || toIndex !in current.steps.indices) return
        val mutable = current.steps.toMutableList()
        val step = mutable.removeAt(fromIndex)
        mutable.add(toIndex, step)
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
                            customerId = customerId,
                            name = current.name.trim(),
                            steps = steps
                        )
                    )
                } else {
                    repository.update(
                        UpdateCustomPresetRequest(
                            id = current.presetId,
                            customerId = customerId,
                            name = current.name.trim(),
                            steps = steps
                        )
                    )
                    current.presetId
                }
                _events.emit(EditorEvent.Saved(presetId))
                val refreshed = repository.getPresetById(presetId)
                val refreshedSteps = refreshed?.steps?.sortedBy { it.order } ?: current.steps.normalizeOrder()
                val refreshedName = refreshed?.name ?: current.name
                _uiState.update {
                    it.copy(
                        presetId = presetId,
                        name = refreshedName,
                        originalName = refreshedName,
                        steps = refreshedSteps,
                        originalSteps = refreshedSteps,
                        isSaving = false,
                        editingStepId = null,
                        frequencyInput = "",
                        intensityInput = "",
                        durationInput = ""
                    ).recomputeCanSave()
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
