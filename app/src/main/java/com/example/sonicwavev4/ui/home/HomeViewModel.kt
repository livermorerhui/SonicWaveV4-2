package com.example.sonicwavev4.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.network.StartOperationRequest
import com.example.sonicwavev4.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import com.example.sonicwavev4.network.Customer

class HomeViewModel (application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager(application.applicationContext)
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
                "frequency" -> _frequency.value = 0
                "intensity" -> _intensity.value = 0
                "time"      -> _timeInMinutes.value = 0
            }
        }
        else if (!bufferValue.isNullOrEmpty()) {
            val numericValue = bufferValue.toIntOrNull() ?: 0
            when (inputType) {
                "frequency" -> _frequency.value = numericValue
                "intensity" -> _intensity.value = numericValue
                "time"      -> _timeInMinutes.value = numericValue
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
                "frequency" -> _frequency.value = (_frequency.value ?: 0).toString().dropLast(1).toIntOrNull() ?: 0
                "intensity" -> _intensity.value = (_intensity.value ?: 0).toString().dropLast(1).toIntOrNull() ?: 0
                "time"      -> _timeInMinutes.value = (_timeInMinutes.value ?: 0).toString().dropLast(1).toIntOrNull() ?: 0
            }
        }
    }

    fun clearCurrentParameter() {
        _inputBuffer.value = ""
        _isEditing.value = false
        when (_currentInputType.value) {
            "frequency" -> _frequency.value = 0
            "intensity" -> _intensity.value = 0
            "time"      -> _timeInMinutes.value = 0
        }
    }

    fun commitAndCycleInputType() {
        commitInputBuffer()
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
            "frequency" -> _frequency.value = max(0, (_frequency.value ?: 0) + delta)
            "intensity" -> _intensity.value = max(0, (_intensity.value ?: 0) + delta)
            "time"      -> _timeInMinutes.value = max(0, (_timeInMinutes.value ?: 0) + delta)
        }
    }

    // --- 公开的增减接口 (无改动) ---
    fun incrementFrequency() = applyDelta("frequency", 1)
    fun decrementFrequency() = applyDelta("frequency", -1)
    fun incrementIntensity() = applyDelta("intensity", 1)
    fun decrementIntensity() = applyDelta("intensity", -1)
    fun incrementTime() = applyDelta("time", 1)
    fun decrementTime() = applyDelta("time", -1)


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
                forceStop()
            }
        }
    }

    fun startStopPlayback(selectedCustomer: Customer?) {
        commitInputBuffer()
        if (_isStarted.value == true) {
            forceStop()
        } else {
            if ((_frequency.value ?: 0) > 0 && (_timeInMinutes.value ?: 0) > 0) {
                handleStartOperation(selectedCustomer)
            }
        }
    }

    fun forceStop() {
        if (_isStarted.value == true) {
            handleStopOperation()
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

    private fun handleStartOperation(selectedCustomer: Customer?) {
        val userId = sessionManager.fetchUserId() ?: "guest"
        val request = StartOperationRequest(
            userId = userId,
            userName = sessionManager.fetchUserName(),
            email = sessionManager.fetchEmail(),
            customer_id = selectedCustomer?.id,
            customer_name = selectedCustomer?.name,
            frequency = frequency.value ?: 0,
            intensity = intensity.value ?: 0,
            operationTime = timeInMinutes.value ?: 0
        )

        viewModelScope.launch {
            try {
                val response = RetrofitClient.api.startOperation(request)
                currentOperationId = response.operationId
                _isStarted.value = true
                startCountdown()
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to start operation", e)
            }
        }
    }

    private fun handleStopOperation() {
        val operationId = currentOperationId ?: return
        viewModelScope.launch {
            try {
                RetrofitClient.api.stopOperation(operationId)
                currentOperationId = null
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to stop operation", e)
            }
        }
    }
}