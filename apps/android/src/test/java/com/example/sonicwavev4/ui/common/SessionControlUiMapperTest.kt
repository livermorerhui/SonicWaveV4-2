package com.example.sonicwavev4.ui.common

import com.example.sonicwavev4.R
import com.example.sonicwavev4.core.vibration.VibrationSessionUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionControlUiMapperTest {

    @Test
    fun `idle state uses start label and start background`() {
        val state = VibrationSessionUiState()

        val ui = SessionControlUiMapper.primaryButtonUi(state)

        assertEquals(R.string.button_start, ui.labelRes)
        assertEquals(R.drawable.bg_home_start_button, ui.backgroundRes)
    }

    @Test
    fun `running state uses pause label and yellow background`() {
        val state = VibrationSessionUiState(isRunning = true)

        val ui = SessionControlUiMapper.primaryButtonUi(state)

        assertEquals(R.string.button_pause, ui.labelRes)
        assertEquals(R.drawable.bg_button_yellow, ui.backgroundRes)
    }

    @Test
    fun `paused state uses resume label and green background`() {
        val state = VibrationSessionUiState(isPaused = true)

        val ui = SessionControlUiMapper.primaryButtonUi(state)

        assertEquals(R.string.button_resume, ui.labelRes)
        assertEquals(R.drawable.bg_jixu_green, ui.backgroundRes)
    }
}
