package com.example.sonicwavev4.ui.home

import android.app.Application
import com.example.sonicwavev4.core.vibration.VibrationHardwareGateway
import com.example.sonicwavev4.core.vibration.VibrationSessionGateway
import com.example.sonicwavev4.core.vibration.VibrationSessionIntent
import com.example.sonicwavev4.data.home.HardwareEvent
import com.example.sonicwavev4.data.home.HardwareState
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.network.OperationEventRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private lateinit var hardware: FakeHardwareGateway
    private lateinit var sessionRepository: FakeSessionGateway
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        hardware = FakeHardwareGateway()
        sessionRepository = FakeSessionGateway()
        viewModel = HomeViewModel(Application(), hardware, sessionRepository)
        viewModel.setSessionActive(true)
        viewModel.updateAccountAccess(true)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun startAndStopFlow_updatesUiStateAndRepositories() = scope.runTest {
        val customer = Customer(
            id = 1,
            name = "Test",
            dateOfBirth = null,
            gender = "M",
            phone = "",
            email = "",
            height = 170.0,
            weight = 60.0
        )

        viewModel.handleIntent(VibrationSessionIntent.SelectInput("frequency"))
        viewModel.handleIntent(VibrationSessionIntent.AppendDigit("5"))
        viewModel.handleIntent(VibrationSessionIntent.CommitAndCycle)
        viewModel.handleIntent(VibrationSessionIntent.AppendDigit("2"))
        viewModel.handleIntent(VibrationSessionIntent.CommitAndCycle)
        viewModel.handleIntent(VibrationSessionIntent.AppendDigit("1"))
        viewModel.handleIntent(VibrationSessionIntent.CommitAndCycle)

        viewModel.handleIntent(VibrationSessionIntent.ToggleStartStop(customer))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isRunning)
        assertEquals(1, sessionRepository.started.size)
        val startArgs = sessionRepository.started.first()
        assertEquals(5, startArgs.frequency)
        assertEquals(2, startArgs.intensity)
        assertEquals(1, startArgs.minutes)
        assertTrue(hardware.startOutputCalls.isNotEmpty())

        viewModel.handleIntent(VibrationSessionIntent.ToggleStartStop(customer))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRunning)
        assertTrue(sessionRepository.stoppedReasons.contains("manual"))
    }

    private class FakeHardwareGateway : VibrationHardwareGateway {
        private val _state = MutableStateFlow(HardwareState(isHardwareReady = true))
        override val state: StateFlow<HardwareState> = _state
        override val events: SharedFlow<HardwareEvent> = MutableSharedFlow()
        val startOutputCalls = mutableListOf<Triple<Int, Int, Boolean>>()
        override fun start() {}
        override suspend fun stop() {}
        override suspend fun applyFrequency(freq: Int) {}
        override suspend fun applyIntensity(intensity: Int) {}
        override suspend fun startOutput(targetFrequency: Int, targetIntensity: Int, playTone: Boolean): Boolean {
            startOutputCalls.add(Triple(targetFrequency, targetIntensity, playTone))
            return true
        }
        override suspend fun stopOutput() {}
        override suspend fun playStandaloneTone(frequency: Int, intensity: Int): Boolean = true
        override fun playTapSound() {}
    }

    private class FakeSessionGateway : VibrationSessionGateway {
        data class StartArgs(val frequency: Int, val intensity: Int, val minutes: Int)
        val started = mutableListOf<StartArgs>()
        val stoppedReasons = mutableListOf<String>()
        override suspend fun startOperation(selectedCustomer: Customer?, frequency: Int, intensity: Int, timeInMinutes: Int): Long {
            started.add(StartArgs(frequency, intensity, timeInMinutes))
            return started.size.toLong()
        }
        override suspend fun stopOperation(operationId: Long, reason: String, detail: String?) {
            stoppedReasons.add(reason)
        }
        override suspend fun logOperationEvent(operationId: Long, request: OperationEventRequest) {}
    }
}
