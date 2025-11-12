package com.example.sonicwavev4.ui.persetmode

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.data.custompreset.CustomPresetRepository
import com.example.sonicwavev4.data.custompreset.model.CustomPreset
import com.example.sonicwavev4.data.custompreset.model.CustomPresetStep
import com.example.sonicwavev4.data.home.HomeHardwareRepository
import com.example.sonicwavev4.data.home.HomeSessionRepository
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.ui.common.UiEvent
import com.example.sonicwavev4.ui.persetmode.modes.AbdomenChest10m
import com.example.sonicwavev4.ui.persetmode.modes.LowerLimb10m
import com.example.sonicwavev4.ui.persetmode.modes.PresetMode
import com.example.sonicwavev4.ui.persetmode.modes.Step
import com.example.sonicwavev4.ui.persetmode.modes.UpperLimbAndHead10m
import com.example.sonicwavev4.ui.persetmode.modes.WholeBody10m
import com.example.sonicwavev4.utils.GlobalLogoutManager
import com.example.sonicwavev4.utils.TestToneSettings
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PresetCategory {
    BUILT_IN,
    CUSTOM
}

private fun CustomPreset.toUiModel(selectedId: String?): CustomPresetUiModel {
    val orderedSteps = steps.sortedBy { it.order }
    val totalDuration = orderedSteps.sumOf { it.durationSec }
    val summary = "共${orderedSteps.size}步 · ${totalDuration}秒"
    return CustomPresetUiModel(
        id = id,
        name = name,
        summary = summary,
        stepCount = orderedSteps.size,
        totalDurationSec = totalDuration,
        isSelected = selectedId == id
    )
}

data class PresetModeSummary(
    val id: String,
    val displayName: String,
    val totalDurationSec: Int,
    val stepCount: Int
)

data class CustomPresetUiModel(
    val id: String,
    val name: String,
    val summary: String,
    val stepCount: Int,
    val totalDurationSec: Int,
    val isSelected: Boolean
)

data class PresetModeUiState(
    val category: PresetCategory = PresetCategory.BUILT_IN,
    val modeSummaries: List<PresetModeSummary> = emptyList(),
    val selectedModeIndex: Int = 0,
    val isRunning: Boolean = false,
    val modeButtonsEnabled: Boolean = true,
    val isStartEnabled: Boolean = false,
    val currentStepIndex: Int? = null,
    val currentStep: Step? = null,
    val frequencyHz: Int? = null,
    val intensity01V: Int? = null,
    val remainingSeconds: Int = 0,
    val totalDurationSeconds: Int = 0,
    val customPresets: List<CustomPresetUiModel> = emptyList(),
    val selectedCustomPresetId: String? = null
)

class PersetmodeViewModel(
    application: Application,
    private val hardwareRepository: HomeHardwareRepository,
    private val sessionRepository: HomeSessionRepository,
    private val customPresetRepository: CustomPresetRepository
) : AndroidViewModel(application) {

    private val presetModes: List<PresetMode> = listOf(
        WholeBody10m,
        UpperLimbAndHead10m,
        AbdomenChest10m,
        LowerLimb10m
    )

    private var customPresetsCache: List<CustomPreset> = emptyList()
    private var pendingCustomSelectionId: String? = null

    private val intensityScalePct = MutableStateFlow(100)

    private val _uiState = MutableStateFlow(buildInitialUiState())
    val uiState: StateFlow<PresetModeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var runJob: Job? = null
    private var currentRunId: Long? = null
    private var runningWithoutHardware = false
    private var isSessionActive = false
    private var isTestAccount = false
    private var lastHardwareReady = false
    private var shouldPlayTone = false

    private enum class StopReason(val apiValue: String) {
        MANUAL("manual"),
        LOGOUT("logout"),
        COUNTDOWN_COMPLETE("countdown_complete"),
        HARDWARE_ERROR("hardware_error"),
        UNKNOWN("unknown")
    }

    init {
        hardwareRepository.start()

        viewModelScope.launch {
            hardwareRepository.state.collect { state ->
                val wasReady = lastHardwareReady
                lastHardwareReady = state.isHardwareReady
                if (!state.isHardwareReady && wasReady && _uiState.value.isRunning) {
                    forceStop(StopReason.HARDWARE_ERROR, detail = "CH341 disconnected or unavailable")
                }
                recomputeStartButtonEnabled()
            }
        }

        viewModelScope.launch {
            hardwareRepository.events.collect { event ->
                when (event) {
                    is com.example.sonicwavev4.data.home.HardwareEvent.Toast -> _events.emit(
                        UiEvent.ShowToast(event.message)
                    )
                    is com.example.sonicwavev4.data.home.HardwareEvent.Error -> _events.emit(
                        UiEvent.ShowError(event.throwable)
                    )
                }
            }
        }

        viewModelScope.launch {
            GlobalLogoutManager.logoutEvent.collect {
                isSessionActive = false
                if (_uiState.value.isRunning) {
                    forceStop(StopReason.LOGOUT)
                } else {
                    recomputeStartButtonEnabled()
                }
            }
        }

        viewModelScope.launch {
            TestToneSettings.sineToneEnabled.collect { desired ->
                val allowed = isTestAccount
                setPlaySineTone(if (allowed) desired else false)
            }
        }

        viewModelScope.launch {
            customPresetRepository.customPresets.collect { presets ->
                customPresetsCache = presets
                val currentSelectedId = _uiState.value.selectedCustomPresetId
                val hasCurrent = currentSelectedId != null && presets.any { it.id == currentSelectedId }
                val normalizedSelectedId = if (hasCurrent) currentSelectedId else null
                _uiState.update { state ->
                    val updated = state.copy(
                        customPresets = presets.map { it.toUiModel(normalizedSelectedId) },
                        selectedCustomPresetId = normalizedSelectedId
                    )
                    if (state.category == PresetCategory.CUSTOM && normalizedSelectedId == null) {
                        updated.copy(
                            frequencyHz = null,
                            intensity01V = null,
                            remainingSeconds = 0,
                            totalDurationSeconds = 0
                        )
                    } else {
                        updated
                    }
                }
                val pendingSelection = pendingCustomSelectionId
                if (pendingSelection != null && presets.any { it.id == pendingSelection }) {
                    pendingCustomSelectionId = null
                    selectCustomPreset(pendingSelection)
                } else {
                    recomputeStartButtonEnabled()
                }
            }
        }
    }

    fun selectMode(index: Int) {
        if (_uiState.value.isRunning) return
        val clamped = index.coerceIn(0, presetModes.lastIndex)
        val mode = presetModes[clamped]
        val steps = scaledSteps(mode)
        val first = steps.firstOrNull()
        _uiState.update {
            it.copy(
                category = PresetCategory.BUILT_IN,
                selectedModeIndex = clamped,
                frequencyHz = first?.frequencyHz,
                intensity01V = first?.intensity01V,
                remainingSeconds = mode.totalDurationSec,
                totalDurationSeconds = mode.totalDurationSec
            )
        }
        recomputeStartButtonEnabled()
    }

    fun enterCustomMode() {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(category = PresetCategory.CUSTOM) }
        recomputeStartButtonEnabled()
    }

    fun selectCustomPreset(presetId: String) {
        if (_uiState.value.isRunning) return
        val preset = customPresetsCache.firstOrNull { it.id == presetId }
        if (preset == null) {
            pendingCustomSelectionId = presetId
            return
        }
        pendingCustomSelectionId = null
        applyCustomPresetSelection(preset)
    }

    fun focusOnCustomPreset(presetId: String) {
        pendingCustomSelectionId = presetId
        selectCustomPreset(presetId)
    }

    fun clearCustomSelection() {
        if (_uiState.value.selectedCustomPresetId == null && _uiState.value.category == PresetCategory.CUSTOM) {
            return
        }
        _uiState.update {
            it.copy(
                category = PresetCategory.CUSTOM,
                selectedCustomPresetId = null,
                customPresets = it.customPresets.map { model -> model.copy(isSelected = false) },
                frequencyHz = null,
                intensity01V = null,
                remainingSeconds = 0,
                totalDurationSeconds = 0
            )
        }
        recomputeStartButtonEnabled()
    }

    private fun applyCustomPresetSelection(preset: CustomPreset) {
        val orderedSteps = preset.steps.sortedBy { it.order }
        val first = orderedSteps.firstOrNull()
        val totalDuration = orderedSteps.sumOf { it.durationSec }
        _uiState.update {
            it.copy(
                category = PresetCategory.CUSTOM,
                selectedCustomPresetId = preset.id,
                customPresets = customPresetsCache.map { presetItem -> presetItem.toUiModel(preset.id) },
                frequencyHz = first?.frequencyHz,
                intensity01V = first?.intensity01V,
                remainingSeconds = totalDuration,
                totalDurationSeconds = totalDuration
            )
        }
        recomputeStartButtonEnabled()
    }

    fun deleteCustomPreset(presetId: String) {
        viewModelScope.launch {
            customPresetRepository.delete(presetId)
        }
        if (_uiState.value.selectedCustomPresetId == presetId) {
            _uiState.update {
                it.copy(
                    selectedCustomPresetId = null,
                    customPresets = it.customPresets.map { preset -> preset.copy(isSelected = false) }
                )
            }
            recomputeStartButtonEnabled()
        }
    }

    fun reorderCustomPresets(newOrderIds: List<String>) {
        viewModelScope.launch {
            customPresetRepository.reorderPresets(newOrderIds)
        }
    }

    fun setIntensityScalePct(value: Int) {
        val clamped = value.coerceIn(10, 200)
        if (intensityScalePct.value == clamped) return
        intensityScalePct.value = clamped
        if (_uiState.value.isRunning) return
        val mode = currentMode()
        val first = scaledSteps(mode).firstOrNull()
        _uiState.update {
            it.copy(
                frequencyHz = first?.frequencyHz,
                intensity01V = first?.intensity01V
            )
        }
    }

    fun setSessionActive(active: Boolean) {
        if (isSessionActive == active) return
        isSessionActive = active
        if (!active) {
            if (_uiState.value.isRunning) {
                forceStop(StopReason.LOGOUT)
            } else {
                recomputeStartButtonEnabled()
            }
        } else {
            recomputeStartButtonEnabled()
        }
    }

    fun updateAccountAccess(testAccount: Boolean) {
        if (isTestAccount == testAccount) return
        isTestAccount = testAccount
        if (testAccount) {
            setPlaySineTone(TestToneSettings.sineToneEnabled.value)
        } else {
            setPlaySineTone(false)
        }
        recomputeStartButtonEnabled()
    }

    fun setPlaySineTone(enabled: Boolean) {
        val allowed = isTestAccount
        val desired = if (allowed) enabled else false
        if (shouldPlayTone == desired) return
        val previous = shouldPlayTone
        shouldPlayTone = desired
        if (_uiState.value.isRunning) {
            viewModelScope.launch {
                try {
                    if (runningWithoutHardware) {
                        if (shouldPlayTone) {
                            val current = _uiState.value.currentStep
                            if (current != null) {
                                val intensity = scaleIntensity(current.intensity01V)
                                hardwareRepository.playStandaloneTone(current.frequencyHz, intensity)
                            }
                        } else {
                            hardwareRepository.stopStandaloneTone()
                        }
                    } else {
                        val current = _uiState.value.currentStep
                        if (current != null) {
                            val intensity = scaleIntensity(current.intensity01V)
                            hardwareRepository.startOutput(
                                targetFrequency = current.frequencyHz,
                                targetIntensity = intensity,
                                playTone = shouldPlayTone
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PersetmodeViewModel", "Failed to toggle sine tone", e)
                    _events.emit(UiEvent.ShowError(e))
                    shouldPlayTone = previous
                }
            }
        }
    }

    fun toggleStartStop(selectedCustomer: Customer?) {
        if (_uiState.value.isRunning) {
            forceStop(StopReason.MANUAL)
            return
        }
        if (!isSessionActive) {
            viewModelScope.launch {
                _events.emit(UiEvent.ShowToast("请先登录账号"))
            }
            return
        }
        val hardwareReady = lastHardwareReady
        if (!hardwareReady && !isTestAccount) {
            viewModelScope.launch {
                _events.emit(UiEvent.ShowToast("硬件初始化中，请稍候"))
            }
            return
        }
        val category = _uiState.value.category
        val steps: List<Step>
        val presetId: String
        val presetName: String
        if (category == PresetCategory.BUILT_IN) {
            val mode = currentMode()
            val modeSteps = scaledSteps(mode)
            if (modeSteps.isEmpty()) {
                viewModelScope.launch {
                    _events.emit(UiEvent.ShowToast("当前预设没有有效步骤"))
                }
                return
            }
            steps = modeSteps
            presetId = mode.id
            presetName = mode.displayName
        } else {
            val preset = selectedCustomPreset()
            if (preset == null) {
                viewModelScope.launch {
                    _events.emit(UiEvent.ShowToast("请先选择自设模式"))
                }
                return
            }
            val customSteps = preset.toSteps()
            if (customSteps.isEmpty()) {
                viewModelScope.launch {
                    _events.emit(UiEvent.ShowToast("自设模式没有有效步骤"))
                }
                return
            }
            steps = customSteps
            presetId = preset.id
            presetName = preset.name
        }
        startPreset(selectedCustomer, presetId = presetId, presetName = presetName, steps = steps, useHardware = hardwareReady)
    }

    private fun buildInitialUiState(): PresetModeUiState {
        val mode = presetModes.first()
        val first = scaledSteps(mode).firstOrNull()
        return PresetModeUiState(
            modeSummaries = presetModes.map {
                PresetModeSummary(
                    id = it.id,
                    displayName = it.displayName,
                    totalDurationSec = it.totalDurationSec,
                    stepCount = it.steps.size
                )
            },
            selectedModeIndex = 0,
            frequencyHz = first?.frequencyHz,
            intensity01V = first?.intensity01V,
            remainingSeconds = mode.totalDurationSec,
            totalDurationSeconds = mode.totalDurationSec,
            isStartEnabled = false
        )
    }

    private fun currentMode(): PresetMode = presetModes[_uiState.value.selectedModeIndex]

    private fun scaledSteps(mode: PresetMode = currentMode()): List<Step> {
        val factor = intensityScalePct.value / 100.0
        return mode.steps.map { step ->
            step.copy(intensity01V = scaleIntensity(step.intensity01V, factor))
        }
    }

    private fun selectedCustomPreset(): CustomPreset? {
        val selectedId = _uiState.value.selectedCustomPresetId ?: return null
        return customPresetsCache.firstOrNull { it.id == selectedId }
    }

    private fun CustomPreset.toSteps(): List<Step> =
        steps.sortedBy { it.order }
            .map { Step(intensity01V = it.intensity01V, frequencyHz = it.frequencyHz, durationSec = it.durationSec) }

    fun stopIfRunning() {
        if (_uiState.value.isRunning || currentRunId != null) {
            forceStop(StopReason.MANUAL)
        }
    }

    fun prepareHardwareForEntry() {
        hardwareRepository.start()
        viewModelScope.launch {
            try {
                hardwareRepository.stopStandaloneTone()
            } catch (e: Exception) {
                Log.w("PersetmodeViewModel", "Failed to stop standalone tone during prepare", e)
            }
            try {
                hardwareRepository.stopOutput()
            } catch (e: Exception) {
                Log.w("PersetmodeViewModel", "Failed to stop hardware output during prepare", e)
            }
        }
    }

    private fun scaleIntensity(raw: Int, factor: Double = intensityScalePct.value / 100.0): Int {
        return (raw * factor).roundToInt().coerceIn(0, 255)
    }

    private fun recomputeStartButtonEnabled() {
        val state = _uiState.value
        val running = state.isRunning
        val hasSelection = when (state.category) {
            PresetCategory.BUILT_IN -> true
            PresetCategory.CUSTOM -> state.selectedCustomPresetId != null
        }
        val enabled = if (running) {
            true
        } else {
            isSessionActive && hasSelection && (lastHardwareReady || isTestAccount)
        }
        _uiState.update { it.copy(isStartEnabled = enabled) }
    }

    private fun startPreset(
        selectedCustomer: Customer?,
        presetId: String,
        presetName: String,
        steps: List<Step>,
        useHardware: Boolean
    ) {
        val first = steps.first()
        val totalDuration = steps.sumOf { it.durationSec }
        val scalePct = intensityScalePct.value
        viewModelScope.launch {
            try {
                hardwareRepository.stopStandaloneTone()
            } catch (e: Exception) {
                Log.w("PersetmodeViewModel", "Failed to stop standalone tone before starting preset", e)
            }
            try {
                hardwareRepository.stopOutput()
            } catch (e: Exception) {
                Log.w("PersetmodeViewModel", "Failed to stop existing hardware output before starting preset", e)
            }
            val runId = try {
                sessionRepository.startPresetModeRun(
                    selectedCustomer = selectedCustomer,
                    presetModeId = presetId,
                    presetModeName = presetName,
                    intensityScalePct = scalePct,
                    totalDurationSec = totalDuration
                )
            } catch (e: Exception) {
                Log.e("PersetmodeViewModel", "Failed to start preset mode run", e)
                _events.emit(UiEvent.ShowError(e))
                return@launch
            }
            val outputStarted = tryStartOutput(first, useHardware)
            if (!outputStarted) {
                try {
                    sessionRepository.stopPresetModeRun(
                        runId,
                        StopReason.HARDWARE_ERROR.apiValue,
                        "Failed to start hardware output"
                    )
                } catch (e: Exception) {
                    Log.e("PersetmodeViewModel", "Failed to rollback preset mode run", e)
                }
                return@launch
            }
            currentRunId = runId
            runningWithoutHardware = !useHardware
            _uiState.update {
                it.copy(
                    isRunning = true,
                    modeButtonsEnabled = false,
                    currentStepIndex = 0,
                    currentStep = first,
                    frequencyHz = first.frequencyHz,
                    intensity01V = first.intensity01V,
                    remainingSeconds = totalDuration,
                    totalDurationSeconds = totalDuration
                )
            }
            recomputeStartButtonEnabled()
            runJob = launch { runPresetSequence(steps, useHardware) }
        }
    }

    private suspend fun tryStartOutput(first: Step, useHardware: Boolean): Boolean {
        val intensity = scaleIntensity(first.intensity01V)
        return try {
            if (useHardware) {
                hardwareRepository.startOutput(
                    targetFrequency = first.frequencyHz,
                    targetIntensity = intensity,
                    playTone = shouldPlayTone
                )
            } else {
                if (shouldPlayTone) {
                    hardwareRepository.playStandaloneTone(first.frequencyHz, intensity)
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("PersetmodeViewModel", "Failed to start output", e)
            _events.emit(UiEvent.ShowError(e))
            false
        }
    }

    private suspend fun applyStep(step: Step, useHardware: Boolean) {
        val intensity = scaleIntensity(step.intensity01V)
        try {
            if (useHardware) {
                hardwareRepository.applyFrequency(step.frequencyHz)
                hardwareRepository.applyIntensity(intensity)
            } else if (shouldPlayTone) {
                hardwareRepository.applyFrequency(step.frequencyHz)
                hardwareRepository.applyIntensity(intensity)
            }
        } catch (e: Exception) {
            Log.e("PersetmodeViewModel", "Failed to apply step", e)
            _events.emit(UiEvent.ShowError(e))
            forceStop(StopReason.HARDWARE_ERROR, e.message)
        }
    }

    private suspend fun runPresetSequence(steps: List<Step>, useHardware: Boolean) {
        var remaining = steps.sumOf { it.durationSec }
        var index = 0
        while (index < steps.size && coroutineContext.isActive) {
            val step = steps[index]
            Log.d(
                "PresetRun",
                "stepState index=$index freq=${step.frequencyHz} intensity=${step.intensity01V} duration=${step.durationSec} remaining=$remaining hardwareReady=$lastHardwareReady runningWithoutHardware=$runningWithoutHardware"
            )
            _uiState.update {
                it.copy(
                    currentStepIndex = index,
                    currentStep = step,
                    frequencyHz = step.frequencyHz,
                    intensity01V = step.intensity01V,
                    remainingSeconds = remaining
                )
            }
            val duration = step.durationSec
            repeat(duration) {
                if (!coroutineContext.isActive) return
                delay(1000)
                remaining = (remaining - 1).coerceAtLeast(0)
                _uiState.update { state ->
                    if (!state.isRunning) state else state.copy(remainingSeconds = remaining)
                }
            }
            val nextIndex = index + 1
            if (nextIndex < steps.size && coroutineContext.isActive) {
                val nextStep = steps[nextIndex]
                Log.d(
                    "PresetRun",
                    "applyStep index=$nextIndex freq=${nextStep.frequencyHz} intensity=${nextStep.intensity01V} scaled=${scaleIntensity(nextStep.intensity01V)} useHardware=$useHardware shouldPlayTone=$shouldPlayTone"
                )
                applyStep(nextStep, useHardware)
            }
            index++
        }
        finalizeRun(StopReason.COUNTDOWN_COMPLETE, detail = null)
    }

    private fun forceStop(reason: StopReason, detail: String? = null) {
        val job = runJob
        runJob = null
        if (job != null) {
            viewModelScope.launch {
                job.cancelAndJoin()
                finalizeRun(reason, detail)
            }
        } else {
            finalizeRun(reason, detail)
        }
    }

    private fun finalizeRun(reason: StopReason, detail: String?) {
        if (!_uiState.value.isRunning && currentRunId == null) {
            recomputeStartButtonEnabled()
            return
        }
        val runId = currentRunId
        val wasSoftwareOnly = runningWithoutHardware
        runningWithoutHardware = false
        currentRunId = null
        runJob = null
        viewModelScope.launch {
            try {
                if (wasSoftwareOnly) {
                    hardwareRepository.stopStandaloneTone()
                } else {
                    hardwareRepository.stopOutput()
                }
            } catch (e: Exception) {
                Log.e("PersetmodeViewModel", "Failed to stop hardware output", e)
            }
            if (runId != null) {
                try {
                    sessionRepository.stopPresetModeRun(runId, reason.apiValue, detail)
                } catch (e: Exception) {
                    Log.e("PersetmodeViewModel", "Failed to log preset mode stop", e)
                }
            }
        }
        val isCustomCategory = _uiState.value.category == PresetCategory.CUSTOM
        val nextFreq: Int?
        val nextIntensity: Int?
        val nextRemaining: Int
        val nextTotal: Int
        if (isCustomCategory) {
            val preset = selectedCustomPreset()
            val steps = preset?.toSteps()
            val first = steps?.firstOrNull()
            val total = steps?.sumOf { it.durationSec } ?: 0
            nextFreq = first?.frequencyHz
            nextIntensity = first?.intensity01V
            nextRemaining = total
            nextTotal = total
        } else {
            val mode = currentMode()
            val first = scaledSteps(mode).firstOrNull()
            nextFreq = first?.frequencyHz
            nextIntensity = first?.intensity01V
            nextRemaining = mode.totalDurationSec
            nextTotal = mode.totalDurationSec
        }
        _uiState.update {
            it.copy(
                isRunning = false,
                modeButtonsEnabled = true,
                currentStepIndex = null,
                currentStep = null,
                frequencyHz = nextFreq,
                intensity01V = nextIntensity,
                remainingSeconds = nextRemaining,
                totalDurationSeconds = nextTotal
            )
        }
        recomputeStartButtonEnabled()
        if (reason == StopReason.COUNTDOWN_COMPLETE) {
            viewModelScope.launch {
                _events.emit(UiEvent.ShowToast("预设模式已完成"))
            }
        }
    }
}
