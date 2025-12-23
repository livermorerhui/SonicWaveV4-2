package com.example.sonicwavev4.ui.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.sonicwavev4.R
import com.example.sonicwavev4.core.vibration.VibrationSessionUiState

data class SessionPrimaryButtonUi(
    @StringRes val labelRes: Int,
    @DrawableRes val backgroundRes: Int
)

object SessionControlUiMapper {
    fun primaryButtonUi(state: VibrationSessionUiState): SessionPrimaryButtonUi {
        return when {
            state.isPaused -> SessionPrimaryButtonUi(R.string.button_resume, R.drawable.bg_jixu_green)
            state.isRunning -> SessionPrimaryButtonUi(R.string.button_pause, R.drawable.bg_button_yellow)
            else -> SessionPrimaryButtonUi(R.string.button_start, R.drawable.bg_home_start_button)
        }
    }
}
