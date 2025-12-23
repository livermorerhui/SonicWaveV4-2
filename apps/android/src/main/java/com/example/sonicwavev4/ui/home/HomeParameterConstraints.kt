package com.example.sonicwavev4.ui.home

/**
 * Home 参数输入范围约束：
 * - 允许 0 作为“未输入/清空”占位值
 * - 其余值限制在业务允许范围内
 */
object HomeParameterConstraints {
    const val MIN_FREQUENCY = 6
    const val MAX_FREQUENCY = 200
    const val MIN_FREQUENCY_INPUT = 1
    const val MIN_INTENSITY = 1
    const val MAX_INTENSITY = 120
    const val MIN_TIME_MINUTES = 1
    const val MAX_TIME_MINUTES = 120

    fun clampFrequency(value: Int): Int = clampOptionalRange(value, MIN_FREQUENCY, MAX_FREQUENCY)
    fun clampFrequencyInput(value: Int): Int = clampOptionalRange(value, MIN_FREQUENCY_INPUT, MAX_FREQUENCY)
    fun clampIntensity(value: Int): Int = clampOptionalRange(value, MIN_INTENSITY, MAX_INTENSITY)
    fun clampTimeMinutes(value: Int): Int = clampOptionalRange(value, MIN_TIME_MINUTES, MAX_TIME_MINUTES)

    private fun clampOptionalRange(value: Int, min: Int, max: Int): Int {
        if (value <= 0) return 0
        return value.coerceIn(min, max)
    }
}
