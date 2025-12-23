package com.example.sonicwavev4.data.custompreset

/**
 * 自定义模式参数约束。
 * 注意：自设模式的时长输入单位为“分钟”，存储仍以秒为单位。
 */
object CustomPresetConstraints {
    const val MIN_FREQUENCY_HZ = 6
    const val MAX_FREQUENCY_HZ = 200

    const val MIN_INTENSITY_01V = 1
    const val MAX_INTENSITY_01V = 120

    const val MIN_DURATION_MIN = 1
    const val MAX_DURATION_MIN = 120
    const val MIN_DURATION_SEC = MIN_DURATION_MIN * 60
    const val MAX_DURATION_SEC = MAX_DURATION_MIN * 60

    const val FREQUENCY_UNIT = "Hz"
    const val INTENSITY_UNIT = "0.01V"
    const val DURATION_UNIT = "分钟"

    fun clampFrequency(value: Int): Int = value.coerceIn(MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ)

    fun clampIntensity(value: Int): Int =
        value.coerceIn(MIN_INTENSITY_01V, MAX_INTENSITY_01V)

    fun clampDurationSeconds(value: Int): Int = value.coerceIn(MIN_DURATION_SEC, MAX_DURATION_SEC)

    fun clampDurationMinutes(value: Int): Int = value.coerceIn(MIN_DURATION_MIN, MAX_DURATION_MIN)

    fun minutesToSeconds(valueMinutes: Int): Int = clampDurationMinutes(valueMinutes) * 60

    fun secondsToMinutesDisplay(valueSeconds: Int): Int {
        if (valueSeconds <= 0) return MIN_DURATION_MIN
        val minutes = (valueSeconds + 59) / 60
        return minutes.coerceIn(MIN_DURATION_MIN, MAX_DURATION_MIN)
    }
}
