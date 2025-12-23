package com.example.sonicwavev4.ui.common

import com.example.sonicwavev4.core.vibration.VibrationSessionUiState

object SoftResumeUi {
    // 平板端：仅在软降激活时显示恢复按钮。
    fun shouldShow(state: VibrationSessionUiState): Boolean {
        return state.softReductionActive
    }
}
