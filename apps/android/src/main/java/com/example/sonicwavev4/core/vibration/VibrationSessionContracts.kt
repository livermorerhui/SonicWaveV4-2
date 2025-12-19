package com.example.sonicwavev4.core.vibration

import com.example.sonicwavev4.data.home.HardwareEvent
import com.example.sonicwavev4.data.home.HardwareState
import com.example.sonicwavev4.core.vibration.ParameterTransitionSpec
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.network.OperationEventRequest
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared abstractions and UI state definitions for vibration session flows.
 * This acts as a reusable template for MVVM conversions in other vibration-related screens.
 */
interface VibrationHardwareGateway {
    val state: StateFlow<HardwareState>
    val events: SharedFlow<HardwareEvent>

    fun start()
    suspend fun stop()
    suspend fun applyFrequency(freq: Int)
    suspend fun applyIntensity(intensity: Int)
    suspend fun startOutput(targetFrequency: Int, targetIntensity: Int, playTone: Boolean = true): Boolean
    suspend fun stopOutput()
    suspend fun playStandaloneTone(frequency: Int, intensity: Int): Boolean
    suspend fun stopStandaloneTone()
    fun playTapSound()

    suspend fun transitionTo(
        targetFrequency: Int,
        targetIntensity: Int,
        spec: ParameterTransitionSpec = ParameterTransitionSpec.DurationSpec(durationMs = 400, tickMs = 20)
    ) {
        applyFrequency(targetFrequency)
        applyIntensity(targetIntensity)
    }
}

interface VibrationSessionGateway {
    suspend fun startOperation(
        selectedCustomer: Customer?,
        frequency: Int,
        intensity: Int,
        timeInMinutes: Int
    ): Long

    suspend fun stopOperation(operationId: Long, reason: String, detail: String?)

    suspend fun logOperationEvent(operationId: Long, request: OperationEventRequest)
}

data class VibrationSessionUiState(
    val frequencyValue: Int = 0,
    val intensityValue: Int = 0,
    val timeInMinutes: Int = 0,
    val countdownSeconds: Int = 0,
    val frequencyDisplay: String = "0",
    val intensityDisplay: String = "0",
    val timeDisplay: String = "00:00",
    val activeInputType: String = "frequency",
    val isEditing: Boolean = false,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val startButtonEnabled: Boolean = false,
    val isHardwareReady: Boolean = false,
    val isTestAccount: Boolean = false,
    val playSineTone: Boolean = false,
    val softReductionActive: Boolean = false,
    val softPanelExpanded: Boolean = false
)

sealed class VibrationSessionIntent {
    data class SelectInput(val type: String) : VibrationSessionIntent()
    data class AppendDigit(val digit: String) : VibrationSessionIntent()
    object DeleteDigit : VibrationSessionIntent()
    object ClearCurrent : VibrationSessionIntent()
    object CommitAndCycle : VibrationSessionIntent()
    data class AdjustFrequency(val delta: Int) : VibrationSessionIntent()
    data class AdjustIntensity(val delta: Int) : VibrationSessionIntent()
    data class AdjustTime(val delta: Int) : VibrationSessionIntent()
    data class ToggleStartStop(val customer: Customer?) : VibrationSessionIntent()
    object ClearAll : VibrationSessionIntent()

    object SoftReduceFromTap : VibrationSessionIntent()
    object SoftReductionStopClicked : VibrationSessionIntent()
    object SoftReductionResumeClicked : VibrationSessionIntent()
    object SoftReductionCollapsePanel : VibrationSessionIntent()
}
