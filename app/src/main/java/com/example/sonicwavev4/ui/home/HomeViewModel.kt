package com.example.sonicwavev4.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class HomeViewModel : ViewModel() {

    // --- 状态值 ---
    private val _frequency = MutableLiveData(0)
    val frequency: LiveData<Int> = _frequency

    private val _intensity = MutableLiveData(0)
    val intensity: LiveData<Int> = _intensity

    private val _timeInMinutes = MutableLiveData(0)
    val timeInMinutes: LiveData<Int> = _timeInMinutes

    // --- 控制值 ---
    private val _isStarted = MutableLiveData(false)
    val isStarted: LiveData<Boolean> = _isStarted

    private val _countdownSeconds = MutableLiveData(0)
    val countdownSeconds: LiveData<Int> = _countdownSeconds

    private var countdownJob: Job? = null

    // --- 键盘输入相关 ---
    private val _currentInputType = MutableLiveData("")
    val currentInputType: LiveData<String> = _currentInputType

    private val _inputBuffer = MutableLiveData("")
    val inputBuffer: LiveData<String> = _inputBuffer

    // --- 【核心改动】删除逻辑 ---
    fun deleteLastFromInputBuffer() {
        val buffer = _inputBuffer.value
        val inputType = _currentInputType.value

        if (!buffer.isNullOrEmpty()) {
            // 情况1：如果缓冲区有内容，则删除缓冲区的最后一个字符
            _inputBuffer.value = buffer.dropLast(1)
        } else if (!inputType.isNullOrEmpty()) {
            // 情况2：如果缓冲区为空，但有高亮目标，则删除已储存值的“最后一位”
            when (inputType) {
                "frequency" -> {
                    val currentValue = _frequency.value ?: 0
                    // 将数字转为字符串，删除最后一位，再转回数字
                    _frequency.value = (currentValue.toString().dropLast(1).toIntOrNull() ?: 0)
                }
                "intensity" -> {
                    val currentValue = _intensity.value ?: 0
                    _intensity.value = (currentValue.toString().dropLast(1).toIntOrNull() ?: 0)
                }
                "time" -> {
                    val currentValue = _timeInMinutes.value ?: 0
                    _timeInMinutes.value = (currentValue.toString().dropLast(1).toIntOrNull() ?: 0)
                }
            }
        }
    }

    // 长按清除逻辑（无改动）
    fun clearCurrentParameter() {
        _inputBuffer.value = ""
        when (_currentInputType.value) {
            "frequency" -> _frequency.value = 0
            "intensity" -> _intensity.value = 0
            "time"      -> _timeInMinutes.value = 0
        }
    }

    // --- 其他所有业务逻辑 (无改动) ---
    fun setCurrentInputType(type: String) {
        if (_currentInputType.value == type) return
        commitInputBuffer()
        _currentInputType.value = type
        _inputBuffer.value = ""
    }
    fun commitAndCycleInputType() {
        commitInputBuffer()
        val nextType = when (_currentInputType.value) {
            "frequency" -> "intensity"; "intensity" -> "time"; else -> "frequency"
        }
        _currentInputType.value = nextType; _inputBuffer.value = ""
    }
    private fun commitInputBuffer() {
        val bufferValue = _inputBuffer.value
        val inputType = _currentInputType.value
        if (bufferValue.isNullOrEmpty() || inputType.isNullOrEmpty()) return
        val numericValue = bufferValue.toIntOrNull() ?: 0
        when (inputType) {
            "frequency" -> _frequency.value = numericValue
            "intensity" -> _intensity.value = numericValue
            "time"      -> _timeInMinutes.value = numericValue
        }
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
            if (_isStarted.value == true) forceStop()
        }
    }
    private fun stopCountdown() { countdownJob?.cancel() }
    fun startStopPlayback() {
        commitInputBuffer()
        val currentState = _isStarted.value ?: false
        if (currentState) {
            forceStop()
        } else {
            if ((_frequency.value ?: 0) > 0 && (_timeInMinutes.value ?: 0) > 0) {
                _isStarted.value = true; startCountdown()
            }
        }
    }
    fun forceStop() { if (_isStarted.value == true) { _isStarted.value = false; stopCountdown() } }
    fun clearAll() {
        _frequency.value = 0; _intensity.value = 0; _timeInMinutes.value = 0; forceStop()
        _currentInputType.value = ""; _inputBuffer.value = ""
    }
    fun updateFrequency(value: Int) { _frequency.value = max(0, value) }
    fun updateIntensity(value: Int) { _intensity.value = max(0, value) }
    fun updateTimeInMinutes(minutes: Int) { _timeInMinutes.value = max(0, minutes) }
    fun appendToInputBuffer(digit: String) {
        if ((_inputBuffer.value?.length ?: 0) < 9) { _inputBuffer.value = (_inputBuffer.value ?: "") + digit }
    }
}