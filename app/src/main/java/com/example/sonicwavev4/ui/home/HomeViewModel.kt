package com.example.sonicwavev4.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import com.example.sonicwavev4.network.* // 导入我们创建的所有网络类
import com.example.sonicwavev4.utils.SessionManager
import java.time.Instant // 用于获取时间

class HomeViewModel (private val sessionManager: SessionManager) : ViewModel() {
    // --- 添加了用于保存操作ID的变量 ---
    private var currentOperationId: Long? = null
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

    private val _isEditing = MutableLiveData(false)
    val isEditing: LiveData<Boolean> = _isEditing

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
        _isEditing.value = false
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
        _isEditing.value = false
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
        val wasEditing = _isEditing.value ?: false
        if (inputType.isNullOrEmpty()) {
            _isEditing.value = false
            return
        }
        // If the user was editing and cleared the buffer, commit 0.
        if (wasEditing && bufferValue.isNullOrEmpty()) {
            when (inputType) {
                "frequency" -> _frequency.value = 0
                "intensity" -> _intensity.value = 0
                "time"      -> _timeInMinutes.value = 0
            }
        } 
        // If the buffer has a value, commit that value.
        else if (!bufferValue.isNullOrEmpty()) {
            val numericValue = bufferValue.toIntOrNull() ?: 0
            when (inputType) {
                "frequency" -> _frequency.value = numericValue
                "intensity" -> _intensity.value = numericValue
                "time"      -> _timeInMinutes.value = numericValue
            }
        }
        // Reset editing state after any commit action
        _isEditing.value = false
    }
    private fun stopCountdown() {
        countdownJob?.cancel()
    }
    // --- MODIFIED: `startCountdown` 现在只负责倒计时 ---
    private fun startCountdown() {
        countdownJob?.cancel()
        val totalSeconds = (_timeInMinutes.value ?: 0) * 60
        _countdownSeconds.value = totalSeconds
        countdownJob = viewModelScope.launch {
            for (i in totalSeconds downTo 0) {
                _countdownSeconds.postValue(i)
                delay(1000)
            }
            // --- MODIFIED: 倒计时结束时，调用 forceStop ---
            if (_isStarted.value == true) {
                Log.d("HomeViewModel", "Countdown finished, forcing stop.")
                forceStop()
            }
        }
    }

    // --- MODIFIED: `startStopPlayback` 现在是主要的业务流程入口 ---
    fun startStopPlayback() {
        commitInputBuffer()
        if (_isStarted.value == true) {
            Log.d("HomeViewModel", "Stop button clicked.")
            forceStop() // 用户点击停止，也调用 forceStop
        } else {
            if ((_frequency.value ?: 0) > 0 && (_timeInMinutes.value ?: 0) > 0) {
                Log.d("HomeViewModel", "Start button clicked.")
                handleStartOperation() // 只调用开始操作
            }
        }
    }

    // --- MODIFIED: `forceStop` 现在也包含网络请求 ---
    fun forceStop() {
        if (_isStarted.value == true) {
            Log.d("HomeViewModel", "forceStop called.")
            handleStopOperation() // 发送网络请求
            _isStarted.value = false
            stopCountdown()
        }
    }
    fun clearAll() {
        if (_isStarted.value == true) {
            forceStop()
        }
        _frequency.value = 0
        _intensity.value = 0
        _timeInMinutes.value = 0
        _currentInputType.value = ""
        _inputBuffer.value = ""
        _isEditing.value = false
    }
    fun updateFrequency(value: Int) { _frequency.value = max(0, value) }
    fun updateIntensity(value: Int) { _intensity.value = max(0, value) }
    fun updateTimeInMinutes(minutes: Int) { _timeInMinutes.value = max(0, minutes) }
    fun appendToInputBuffer(digit: String) {
        if ((_inputBuffer.value?.length ?: 0) < 9) { 
            _inputBuffer.value = (_inputBuffer.value ?: "") + digit 
            _isEditing.value = true
        }
    }

    // --- MODIFIED: `handleStartOperation` 现在负责在成功后更新UI状态 ---
    private fun handleStartOperation() {
        // --- MODIFIED: 使用SessionManager获取所有用户信息 ---
        val userId = sessionManager.fetchUserId() ?: "guest"
        val userName = sessionManager.fetchUserName()
        val email = sessionManager.fetchEmail()
        val customer: String? = null // customer信息我们还未保存，暂时为null

        val request = StartOperationRequest(
            userId = userId,
            userName = userName,
            email = email,
            customer = customer,
            frequency = frequency.value ?: 0,
            intensity = intensity.value ?: 0,
            operationTime = timeInMinutes.value ?: 0
        )

        viewModelScope.launch {
            try {
                Log.d("HomeViewModel", "Starting operation via network...")
                val response = RetrofitClient.api.startOperation(request)
                currentOperationId = response.operationId
                Log.d("HomeViewModel", "Operation started successfully with server ID: $currentOperationId")

                // 只有在网络请求成功后，才更新UI状态和启动倒计时
                _isStarted.value = true
                startCountdown()

            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to start operation", e)
                // 如果网络请求失败，则不启动任何东西，UI保持原样
            }
        }
    }

    private fun handleStopOperation() {
        val operationId = currentOperationId
        if (operationId == null) {
            Log.w("HomeViewModel", "Stop called but no active operation ID found.")
            return
        }

        viewModelScope.launch {
            try {
                Log.d("HomeViewModel", "Stopping operation via network for ID: $operationId")
                RetrofitClient.api.stopOperation(operationId)
                currentOperationId = null
                Log.d("HomeViewModel", "Operation stopped successfully on server.")
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to stop operation", e)
            }
        }
    }
}