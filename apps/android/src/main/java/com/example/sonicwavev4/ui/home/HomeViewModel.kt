package com.example.sonicwavev4.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.R
import com.example.sonicwavev4.core.vibration.VibrationHardwareGateway
import com.example.sonicwavev4.core.vibration.VibrationSessionGateway
import com.example.sonicwavev4.core.vibration.VibrationSessionIntent
import com.example.sonicwavev4.core.vibration.VibrationSessionUiState
import com.example.sonicwavev4.data.home.HardwareEvent
import com.example.sonicwavev4.network.OperationEventRequest
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.ui.common.UiEvent
import com.example.sonicwavev4.utils.GlobalLogoutManager
import com.example.sonicwavev4.utils.TestToneSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.max
import java.util.Locale

class HomeViewModel(
    application: Application,
    private val hardwareRepository: VibrationHardwareGateway,
    private val sessionRepository: VibrationSessionGateway
) : AndroidViewModel(application) {

    // --- 变量和状态 (无改动) ---
    private var currentOperationId: Long? = null
    private val _frequency = MutableLiveData(0)
    val frequency: LiveData<Int> = _frequency
    private val _intensity = MutableLiveData(0)
    val intensity: LiveData<Int> = _intensity
    private val _timeInMinutes = MutableLiveData(0)
    val timeInMinutes: LiveData<Int> = _timeInMinutes
    private val _isStarted = MutableLiveData(false)
    val isStarted: LiveData<Boolean> = _isStarted
    private val _countdownSeconds = MutableLiveData(0)
    val countdownSeconds: LiveData<Int> = _countdownSeconds
    private var countdownJob: Job? = null
    private val _currentInputType = MutableLiveData("frequency")
    val currentInputType: LiveData<String> = _currentInputType
    private val _inputBuffer = MutableLiveData("")
    val inputBuffer: LiveData<String> = _inputBuffer
    private val _isEditing = MutableLiveData(false)
    val isEditing: LiveData<Boolean> = _isEditing

    private val _isTestAccount = MutableLiveData(false)
    val isTestAccount: LiveData<Boolean> = _isTestAccount
    private val _playSineTone = MutableLiveData(false)
    val playSineTone: LiveData<Boolean> = _playSineTone
    private val _startButtonEnabled = MutableLiveData(false)
    val startButtonEnabled: LiveData<Boolean> = _startButtonEnabled

    private var softOriginalIntensity: Int? = null
    private var softReductionJob: Job? = null
    private var softReductionActive: Boolean = false
    private var softPanelExpanded: Boolean = false
    private val softTargetIntensity: Int = 20

    private val _uiState = MutableStateFlow(buildUiState())
    val uiState: StateFlow<VibrationSessionUiState> = _uiState.asStateFlow()

    val hardwareState = hardwareRepository.state.asLiveData()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()
    private var runningWithoutHardware = false
    private var isSessionActive = false
    private var isPaused = false
    companion object {
        private const val LOGIN_REQUIRED_MESSAGE = "请先登录账号"
        private const val HARDWARE_NOT_READY_MESSAGE = "硬件初始化中，请稍候"
    }

    private enum class StopReason(val apiValue: String) {
        MANUAL("manual"),
        LOGOUT("logout"),
        COUNTDOWN_COMPLETE("countdown_complete"),
        HARDWARE_ERROR("hardware_error"),
        UNKNOWN("unknown")
    }

    private enum class OperationEventType(val apiValue: String) {
        ADJUST_FREQUENCY("adjust_frequency"),
        ADJUST_INTENSITY("adjust_intensity"),
        ADJUST_TIME("adjust_time")
    }

    private fun resolveDisplayValue(
        activeType: String?,
        targetType: String,
        buffer: String,
        editing: Boolean,
        committedValue: Int
    ): Int {
        return if (activeType == targetType && (editing || buffer.isNotEmpty())) {
            buffer.toIntOrNull() ?: 0
        } else {
            committedValue
        }
    }

    private fun formatCountdown(seconds: Int): String {
        val minutesPart = seconds / 60
        val secondsPart = seconds % 60
        return String.format(Locale.ROOT, "%02d:%02d", minutesPart, secondsPart)
    }

    private fun buildUiState(): VibrationSessionUiState {
        val activeType = _currentInputType.value
        val buffer = _inputBuffer.value ?: ""
        val editing = _isEditing.value == true
        val running = _isStarted.value == true
        val frequencyValue = resolveDisplayValue(activeType, "frequency", buffer, editing, _frequency.value ?: 0)
        val intensityValue = resolveDisplayValue(activeType, "intensity", buffer, editing, _intensity.value ?: 0)
        val timeValue = resolveDisplayValue(activeType, "time", buffer, editing, _timeInMinutes.value ?: 0)
        val countdownValue = _countdownSeconds.value ?: 0

        val timeDisplay = when {
            running -> formatCountdown(countdownValue)
            isPaused -> formatCountdown(countdownValue)
            else -> getApplication<Application>().getString(R.string.time_minutes_format, timeValue)
        }

        return VibrationSessionUiState(
            frequencyValue = frequencyValue,
            intensityValue = intensityValue,
            timeInMinutes = _timeInMinutes.value ?: 0,
            countdownSeconds = countdownValue,
            frequencyDisplay = getApplication<Application>().getString(R.string.frequency_format, frequencyValue),
            intensityDisplay = intensityValue.toString(),
            timeDisplay = timeDisplay,
            activeInputType = activeType ?: "",
            isEditing = editing,
            isRunning = running,
            isPaused = isPaused,
            startButtonEnabled = _startButtonEnabled.value == true,
            isHardwareReady = hardwareRepository.state.value.isHardwareReady,
            isTestAccount = _isTestAccount.value == true,
            playSineTone = _playSineTone.value == true,
            softReductionActive = softReductionActive,
            softPanelExpanded = softPanelExpanded
        )
    }

    private fun emitUiState() {
        _uiState.value = buildUiState()
    }

    private fun clearSoftReductionState() {
        softReductionJob?.cancel()
        softReductionJob = null
        softOriginalIntensity = null
        softReductionActive = false
        softPanelExpanded = false
    }

    private fun startSoftReductionRamp() {
        softReductionJob?.cancel()

        val target = softTargetIntensity

        softReductionJob = viewModelScope.launch {
            var current = _intensity.value ?: 0

            if (current <= target) {
                if (current != target) {
                    updateIntensity(target)
                }
                return@launch
            }

            while (softReductionActive && _isStarted.value == true && current > target) {
                val delta = max(5, (current - target) / 10)
                current = max(target, current - delta)
                updateIntensity(current)
                delay(80)
            }
        }
    }

    init {
        hardwareRepository.start()
        viewModelScope.launch {
            var wasReady = false
            hardwareRepository.state.collect { state ->
                if (!state.isHardwareReady && wasReady) {
                    forceStop(StopReason.HARDWARE_ERROR, "CH341 disconnected or unavailable")
                    resetUiStateToDefaults()
                }
                wasReady = state.isHardwareReady
                recomputeStartButtonEnabled()
            }
        }
        viewModelScope.launch {
            hardwareRepository.events.collect { event ->
                when (event) {
                    is HardwareEvent.Toast -> _events.emit(UiEvent.ShowToast(event.message))
                    is HardwareEvent.Error -> {
                        Log.e("HomeViewModel", "Hardware error", event.throwable)
                        _events.emit(UiEvent.ShowError(event.throwable))
                    }
                }
            }
        }
        viewModelScope.launch {
            GlobalLogoutManager.logoutEvent.collect {
                isSessionActive = false
                forceStop(StopReason.LOGOUT)
            }
        }
        viewModelScope.launch {
            TestToneSettings.sineToneEnabled.collect { desired ->
                val allowed = _isTestAccount.value == true
                setPlaySineTone(desired && allowed)
            }
        }
        _isTestAccount.value = false
        recomputeStartButtonEnabled()
        emitUiState()
    }

    fun setPlaySineTone(enabled: Boolean) {
        if (_playSineTone.value == enabled) return
        _playSineTone.value = enabled
        emitUiState()
        if (_isStarted.value == true) {
            viewModelScope.launch {
                val success = try {
                    if (runningWithoutHardware) {
                        if (enabled) {
                            hardwareRepository.playStandaloneTone(
                                frequency = frequency.value ?: 0,
                                intensity = intensity.value ?: 0
                            )
                        } else {
                            hardwareRepository.stopStandaloneTone()
                            true
                        }
                    } else {
                        hardwareRepository.startOutput(
                            targetFrequency = frequency.value ?: 0,
                            targetIntensity = intensity.value ?: 0,
                            playTone = enabled
                        )
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Failed to toggle sine tone", e)
                    _events.emit(UiEvent.ShowError(e))
                    false
                }
                if (!success) {
                    _playSineTone.value = !enabled
                    emitUiState()
                }
            }
        }
    }

    fun updateAccountAccess(isTestAccount: Boolean) {
        if (_isTestAccount.value == isTestAccount) {
            return
        }
        _isTestAccount.value = isTestAccount
        val desired = if (isTestAccount) {
            TestToneSettings.sineToneEnabled.value
        } else {
            false
        }
        setPlaySineTone(desired)
        recomputeStartButtonEnabled()
        emitUiState()
    }

    fun setSessionActive(active: Boolean) {
        if (isSessionActive == active) return
        isSessionActive = active
        if (!active) {
            updateAccountAccess(false)
            if (_isStarted.value == true) {
                forceStop(StopReason.LOGOUT)
            } else {
                recomputeStartButtonEnabled()
            }
        } else {
            recomputeStartButtonEnabled()
        }
        emitUiState()
    }

    private fun recomputeStartButtonEnabled() {
        val hardwareReady = hardwareRepository.state.value.isHardwareReady
        val started = _isStarted.value == true
        val testAccount = _isTestAccount.value == true
        _startButtonEnabled.value =
            if (started) {
                true
            } else if (!isSessionActive) {
                false
            } else {
                hardwareReady || testAccount
            }
        emitUiState()
    }

    // --- 缓冲区和参数控制 (无改动) ---
    private fun commitInputBuffer() {
        val bufferValue = _inputBuffer.value
        val inputType = _currentInputType.value
        val wasEditing = _isEditing.value ?: false
        if (inputType.isNullOrEmpty()) {
            _isEditing.value = false
            emitUiState()
            return
        }
        if (wasEditing && bufferValue.isNullOrEmpty()) {
            when (inputType) {
                "frequency" -> updateFrequency(0)
                "intensity" -> updateIntensity(0)
                "time"      -> updateTime(0)
            }
        }
        else if (!bufferValue.isNullOrEmpty()) {
            val numericValue = bufferValue.toIntOrNull() ?: 0
            when (inputType) {
                "frequency" -> updateFrequency(numericValue)
                "intensity" -> updateIntensity(numericValue)
                "time"      -> updateTime(numericValue)
            }
        }
        _inputBuffer.value = ""
        _isEditing.value = false
        emitUiState()
    }

    fun setCurrentInputType(type: String) {
        if (_currentInputType.value == type) return
        commitInputBuffer()
        _currentInputType.value = type
        _inputBuffer.value = ""
        _isEditing.value = false
        emitUiState()
    }

    fun appendToInputBuffer(digit: String) {
        if ((_inputBuffer.value?.length ?: 0) < 9) {
            _inputBuffer.value = (_inputBuffer.value ?: "") + digit
            _isEditing.value = true
            emitUiState()
        }
    }

    fun deleteLastFromInputBuffer() {
        val buffer = _inputBuffer.value
        if (!buffer.isNullOrEmpty()) {
            _inputBuffer.value = buffer.dropLast(1)
        } else {
            when (_currentInputType.value) {
                "frequency" -> {
                    val newValue = (_frequency.value ?: 0).toString().dropLast(1).toIntOrNull() ?: 0
                    updateFrequency(newValue)
                }
                "intensity" -> {
                    val newValue = (_intensity.value ?: 0).toString().dropLast(1).toIntOrNull() ?: 0
                    updateIntensity(newValue)
                }
                "time"      -> {
                    val newValue = (_timeInMinutes.value ?: 0).toString().dropLast(1).toIntOrNull() ?: 0
                    updateTime(newValue)
                }
            }
        }
        emitUiState()
    }

    fun clearCurrentParameter() {
        _inputBuffer.value = ""
        _isEditing.value = false
        when (_currentInputType.value) {
            "frequency" -> updateFrequency(0)
            "intensity" -> updateIntensity(0)
            "time"      -> updateTime(0)
        }
        emitUiState()
    }

    fun commitAndCycleInputType() {
        commitInputBuffer()
        if (_isStarted.value == true) {
            return
        }
        val nextType = when (_currentInputType.value) {
            "frequency" -> "intensity"
            "intensity" -> "time"
            else -> "frequency"
        }
        setCurrentInputType(nextType)
    }

    // --- 【核心优化】集中的增减逻辑 ---
    private fun applyDelta(type: String, delta: Int) {
        // 步骤 1: 切换到正确的参数类型，这会顺便提交上一个参数的缓冲区（如果类型不同）
        setCurrentInputType(type)

        // 步骤 2: 无条件提交当前参数的缓冲区，确保键盘输入的值被计算在内
        commitInputBuffer()

        // 步骤 3: 在最新的、已同步的值上进行安全的加减操作
        when (type) {
            "frequency" -> updateFrequency(max(0, (_frequency.value ?: 0) + delta))
            "intensity" -> updateIntensity(max(0, (_intensity.value ?: 0) + delta))
            "time"      -> updateTime(max(0, (_timeInMinutes.value ?: 0) + delta))
        }
    }

    fun handleIntent(intent: VibrationSessionIntent) {
        when (intent) {
            is VibrationSessionIntent.SelectInput -> setCurrentInputType(intent.type)
            is VibrationSessionIntent.AppendDigit -> appendToInputBuffer(intent.digit)
            VibrationSessionIntent.DeleteDigit -> deleteLastFromInputBuffer()
            VibrationSessionIntent.ClearCurrent -> clearCurrentParameter()
            VibrationSessionIntent.CommitAndCycle -> commitAndCycleInputType()
            is VibrationSessionIntent.AdjustFrequency -> adjustFrequency(intent.delta)
            is VibrationSessionIntent.AdjustIntensity -> adjustIntensity(intent.delta)
            is VibrationSessionIntent.AdjustTime -> applyDelta("time", intent.delta)
            is VibrationSessionIntent.ToggleStartStop -> startStopPlayback(intent.customer)
            VibrationSessionIntent.ClearAll -> clearAll()
            VibrationSessionIntent.SoftReduceFromTap -> handleSoftReduceFromTap()
            VibrationSessionIntent.SoftReductionStopClicked -> handleSoftReductionStopClicked()
            VibrationSessionIntent.SoftReductionResumeClicked -> handleSoftReductionResumeClicked()
            VibrationSessionIntent.SoftReductionCollapsePanel -> handleSoftReductionCollapsePanel()
        }
    }

    private fun handleSoftReduceFromTap() {
        if (softReductionActive) return
        if (_isStarted.value != true) return

        val current = _intensity.value ?: 0
        if (softOriginalIntensity == null) {
            softOriginalIntensity = current
        }

        softReductionActive = true
        softPanelExpanded = true

        startSoftReductionRamp()
        emitUiState()
    }

    private fun handleSoftReductionStopClicked() {
        if (_isStarted.value == true) {
            forceStop(StopReason.MANUAL)
        }

        clearSoftReductionState()
    }

    private fun handleSoftReductionResumeClicked() {
        val original = softOriginalIntensity

        if (_isStarted.value == true && original != null) {
            softReductionJob?.cancel()
            updateIntensity(original)
        }

        clearSoftReductionState()
        emitUiState()
    }

    private fun handleSoftReductionCollapsePanel() {
        if (!softReductionActive) return
        softPanelExpanded = false
        emitUiState()
    }

    // --- 公开的增减接口 (无改动) ---
    fun incrementFrequency() = applyDelta("frequency", 1)
    fun decrementFrequency() = applyDelta("frequency", -1)
    fun incrementIntensity() = applyDelta("intensity", 1)
    fun decrementIntensity() = applyDelta("intensity", -1)
    fun incrementTime() = applyDelta("time", 1)
    fun decrementTime() = applyDelta("time", -1)
    fun adjustFrequency(delta: Int) = applyDelta("frequency", delta)
    fun adjustIntensity(delta: Int) = applyDelta("intensity", delta)


    // --- 播放控制和网络请求 (无改动) ---
    private fun stopCountdown() {
        countdownJob?.cancel()
    }

    private fun startCountdown(resume: Boolean = false) {
        countdownJob?.cancel()
        val totalSeconds = (_timeInMinutes.value ?: 0) * 60
        val startSeconds = if (resume) _countdownSeconds.value ?: totalSeconds else totalSeconds
        _countdownSeconds.value = startSeconds
        emitUiState()
        countdownJob = viewModelScope.launch {
            for (i in startSeconds downTo 0) {
                _countdownSeconds.value = i
                emitUiState()
                delay(1000)
            }
            if (_isStarted.value == true) {
                forceStop(StopReason.COUNTDOWN_COMPLETE)
            }
        }
    }

    fun startStopPlayback(selectedCustomer: Customer?) {
        commitInputBuffer()
        if (!isSessionActive) {
            viewModelScope.launch {
                _events.emit(UiEvent.ShowToast(LOGIN_REQUIRED_MESSAGE))
            }
            return
        }
        val hardwareReady = hardwareRepository.state.value.isHardwareReady
        if (_isStarted.value == true) {
            pausePlayback()
        } else if (isPaused) {
            resumePlayback(hardwareReady)
        } else {
            val isTestAccount = _isTestAccount.value == true
            if (!hardwareReady && !isTestAccount) {
                viewModelScope.launch {
                    _events.emit(UiEvent.ShowToast(HARDWARE_NOT_READY_MESSAGE))
                }
                return
            }
            if ((_frequency.value ?: 0) > 0 && (_timeInMinutes.value ?: 0) > 0) {
                if (_currentInputType.value == "time") {
                    _currentInputType.value = "frequency"
                    _inputBuffer.value = ""
                    _isEditing.value = false
                    emitUiState()
                }
                val shouldPlayTone = _playSineTone.value == true
                    isPaused = false
                    handleStartOperation(
                        selectedCustomer = selectedCustomer,
                        useHardware = hardwareReady,
                        playTone = shouldPlayTone
                    )
                }
        }
    }

    fun stopPlaybackIfRunning() {
        if (_isStarted.value == true || currentOperationId != null) {
            forceStop(StopReason.MANUAL)
        }
    }

    private fun pausePlayback() {
        if (_isStarted.value != true) return
        isPaused = true
        _isStarted.value = false
        stopCountdown()
        viewModelScope.launch {
            try {
                if (runningWithoutHardware) {
                    hardwareRepository.stopStandaloneTone()
                } else {
                    hardwareRepository.stopOutput()
                }
            } catch (e: Exception) {
                Log.w("HomeViewModel", "Failed to pause output cleanly", e)
            }
        }
        recomputeStartButtonEnabled()
        emitUiState()
    }

    private fun resumePlayback(hardwareReady: Boolean) {
        if (!isPaused) return
        val isTestAccount = _isTestAccount.value == true
        if (!hardwareReady && !isTestAccount) {
            viewModelScope.launch { _events.emit(UiEvent.ShowToast(HARDWARE_NOT_READY_MESSAGE)) }
            return
        }
        val targetFrequency = frequency.value ?: 0
        val targetIntensity = intensity.value ?: 0
        val playTone = _playSineTone.value == true
        viewModelScope.launch {
            val outputStarted = try {
                if (runningWithoutHardware) {
                    if (playTone) {
                        hardwareRepository.playStandaloneTone(targetFrequency, targetIntensity)
                    } else {
                        true
                    }
                } else {
                    hardwareRepository.startOutput(
                        targetFrequency = targetFrequency,
                        targetIntensity = targetIntensity,
                        playTone = playTone
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to resume output", e)
                _events.emit(UiEvent.ShowError(e))
                false
            }
            if (outputStarted) {
                _isStarted.value = true
                isPaused = false
                recomputeStartButtonEnabled()
                startCountdown(resume = true)
            }
            emitUiState()
        }
    }

    fun stopSession() {
        viewModelScope.launch {
            stopPlaybackIfRunning()
            try {
                hardwareRepository.stop()
            } catch (e: Exception) {
                Log.w("HomeViewModel", "Failed to stop hardware cleanly", e)
            }
        }
    }

    fun prepareHardwareForEntry() {
        hardwareRepository.start()
        viewModelScope.launch {
            try {
                hardwareRepository.stopStandaloneTone()
            } catch (e: Exception) {
                Log.w("HomeViewModel", "Failed to stop standalone tone during prepare", e)
            }
            try {
                hardwareRepository.stopOutput()
            } catch (e: Exception) {
                Log.w("HomeViewModel", "Failed to stop hardware output during prepare", e)
            }
        }
    }

    private fun forceStop(reason: StopReason = StopReason.MANUAL, detail: String? = null) {
        val wasRunning = _isStarted.value == true
        val hadOperation = currentOperationId != null
        if (wasRunning || hadOperation) {
            handleStopOperation(reason, detail)
        }
        if (wasRunning) {
            _isStarted.value = false
        }
        isPaused = false
        stopCountdown()
        clearSoftReductionState()
        recomputeStartButtonEnabled()
    }

    fun clearAll() {
        if (_isStarted.value == true) {
            forceStop(StopReason.MANUAL)
        }
        isPaused = false
        updateFrequency(0)
        updateIntensity(0)
        updateTime(0)
        _currentInputType.value = ""
        _inputBuffer.value = ""
        _isEditing.value = false
        emitUiState()
    }

    private fun handleStartOperation(selectedCustomer: Customer?, useHardware: Boolean, playTone: Boolean) {
        viewModelScope.launch {
            val targetFrequency = frequency.value ?: 0
            val targetIntensity = intensity.value ?: 0
            try {
                val operationId = sessionRepository.startOperation(
                    selectedCustomer = selectedCustomer,
                    frequency = targetFrequency,
                    intensity = targetIntensity,
                    timeInMinutes = timeInMinutes.value ?: 0
                )
                val outputStarted = if (useHardware) {
                    hardwareRepository.startOutput(
                        targetFrequency = targetFrequency,
                        targetIntensity = targetIntensity,
                        playTone = playTone
                    )
                } else {
                    if (playTone) {
                        hardwareRepository.playStandaloneTone(
                            frequency = targetFrequency,
                            intensity = targetIntensity
                        )
                    } else {
                        true
                    }
                }
                if (outputStarted) {
                    runningWithoutHardware = !useHardware
                    currentOperationId = operationId
                    _isStarted.value = true
                    isPaused = false
                    recomputeStartButtonEnabled()
                    startCountdown()
                } else {
                    runningWithoutHardware = false
                    sessionRepository.stopOperation(
                        operationId,
                        StopReason.HARDWARE_ERROR.apiValue,
                        "Failed to start hardware output"
                    )
                }
            } catch (e: Exception) {
                runningWithoutHardware = false
                Log.e("HomeViewModel", "Failed to start operation", e)
                _events.emit(UiEvent.ShowError(e))
            }
        }
    }

    private fun handleStopOperation(reason: StopReason, detail: String?) {
        val operationId = currentOperationId
        val wasSoftwareOnly = runningWithoutHardware
        runningWithoutHardware = false
        if (operationId != null) {
            viewModelScope.launch {
                try {
                    sessionRepository.stopOperation(operationId, reason.apiValue, detail)
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Failed to stop operation", e)
                } finally {
                    currentOperationId = null
                }
            }
        } else {
            currentOperationId = null
        }
        viewModelScope.launch {
            if (wasSoftwareOnly) {
                hardwareRepository.stopStandaloneTone()
            } else {
                hardwareRepository.stopOutput()
            }
        }
    }

    private fun updateFrequency(value: Int) {
        if (_frequency.value != value) {
            _frequency.value = value
        }
        viewModelScope.launch {
            hardwareRepository.applyFrequency(value)
        }
        if (shouldLogOperationEvents()) {
            logOperationEvent(OperationEventType.ADJUST_FREQUENCY)
        }
        emitUiState()
    }

    private fun updateIntensity(value: Int) {
        if (_intensity.value != value) {
            _intensity.value = value
        }
        viewModelScope.launch {
            hardwareRepository.applyIntensity(value)
        }
        if (shouldLogOperationEvents()) {
            logOperationEvent(OperationEventType.ADJUST_INTENSITY)
        }
        emitUiState()
    }

    private fun updateTime(value: Int) {
        if (_timeInMinutes.value != value) {
            _timeInMinutes.value = value
            if (shouldLogOperationEvents()) {
                logOperationEvent(OperationEventType.ADJUST_TIME)
            }
        }
        emitUiState()
    }

    private fun shouldLogOperationEvents(): Boolean {
        return isSessionActive && currentOperationId != null && (_isStarted.value == true)
    }

    private fun logOperationEvent(type: OperationEventType, detail: String? = null) {
        if (!shouldLogOperationEvents()) return
        val operationId = currentOperationId ?: return
        val currentFrequency = _frequency.value
        val currentIntensity = _intensity.value
        val remainingSeconds = _countdownSeconds.value

        viewModelScope.launch {
            try {
                sessionRepository.logOperationEvent(
                    operationId,
                    OperationEventRequest(
                        eventType = type.apiValue,
                        frequency = currentFrequency,
                        intensity = currentIntensity,
                        timeRemaining = remainingSeconds,
                        extraDetail = detail
                    )
                )
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to log operation event", e)
            }
        }
    }

    fun playTapSound() {
        hardwareRepository.playTapSound()
    }

    override fun onCleared() {
        super.onCleared()
        stopPlaybackIfRunning()
    }

    private suspend fun resetUiStateToDefaults() {
        clearSoftReductionState()
        countdownJob?.cancel()
        _isStarted.value = false
        currentOperationId = null
        runningWithoutHardware = false
        if ((_frequency.value ?: 0) != 0) {
            _frequency.value = 0
        }
        hardwareRepository.applyFrequency(0)
        if ((_intensity.value ?: 0) != 0) {
            _intensity.value = 0
        }
        hardwareRepository.applyIntensity(0)
        if ((_timeInMinutes.value ?: 0) != 0) {
            _timeInMinutes.value = 0
        } else {
            _timeInMinutes.value = 0
        }
        _currentInputType.value = "frequency"
        _inputBuffer.value = ""
        _isEditing.value = false
        _countdownSeconds.value = 0
        recomputeStartButtonEnabled()
    }
}
