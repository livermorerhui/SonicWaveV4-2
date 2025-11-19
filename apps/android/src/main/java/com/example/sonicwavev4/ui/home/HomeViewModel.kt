package com.example.sonicwavev4.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.data.home.HardwareEvent
import com.example.sonicwavev4.data.home.HomeHardwareRepository
import com.example.sonicwavev4.data.home.HomeSessionRepository
import com.example.sonicwavev4.network.OperationEventRequest
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.ui.common.UiEvent
import com.example.sonicwavev4.utils.GlobalLogoutManager
import com.example.sonicwavev4.utils.TestToneSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.max

class HomeViewModel(
    application: Application,
    private val hardwareRepository: HomeHardwareRepository,
    private val sessionRepository: HomeSessionRepository
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

    val hardwareState = hardwareRepository.state.asLiveData()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()
    private var runningWithoutHardware = false
    private var isSessionActive = false
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
    }

    fun setPlaySineTone(enabled: Boolean) {
        if (_playSineTone.value == enabled) return
        _playSineTone.value = enabled
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
    }

    // --- 缓冲区和参数控制 (无改动) ---
    private fun commitInputBuffer() {
        val bufferValue = _inputBuffer.value
        val inputType = _currentInputType.value
        val wasEditing = _isEditing.value ?: false
        if (inputType.isNullOrEmpty()) {
            _isEditing.value = false
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
    }

    fun setCurrentInputType(type: String) {
        if (_currentInputType.value == type) return
        commitInputBuffer()
        _currentInputType.value = type
        _inputBuffer.value = ""
        _isEditing.value = false
    }

    fun appendToInputBuffer(digit: String) {
        if ((_inputBuffer.value?.length ?: 0) < 9) {
            _inputBuffer.value = (_inputBuffer.value ?: "") + digit
            _isEditing.value = true
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
    }

    fun clearCurrentParameter() {
        _inputBuffer.value = ""
        _isEditing.value = false
        when (_currentInputType.value) {
            "frequency" -> updateFrequency(0)
            "intensity" -> updateIntensity(0)
            "time"      -> updateTime(0)
        }
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

    private fun startCountdown() {
        countdownJob?.cancel()
        val totalSeconds = (_timeInMinutes.value ?: 0) * 60
        _countdownSeconds.value = totalSeconds
        countdownJob = viewModelScope.launch {
            for (i in totalSeconds downTo 0) {
                _countdownSeconds.postValue(i)
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
            forceStop(StopReason.MANUAL)
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
                }
                val shouldPlayTone = _playSineTone.value == true
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
        stopCountdown()
        recomputeStartButtonEnabled()
    }

    fun clearAll() {
        if (_isStarted.value == true) {
            forceStop(StopReason.MANUAL)
        }
        updateFrequency(0)
        updateIntensity(0)
        updateTime(0)
        _currentInputType.value = ""
        _inputBuffer.value = ""
        _isEditing.value = false
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
    }

    private fun updateTime(value: Int) {
        if (_timeInMinutes.value != value) {
            _timeInMinutes.value = value
            if (shouldLogOperationEvents()) {
                logOperationEvent(OperationEventType.ADJUST_TIME)
            }
        }
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
