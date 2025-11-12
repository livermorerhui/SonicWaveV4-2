package com.example.sonicwavev4.data.custompreset

/**
 * 自定义模式参数约束。
 * 目前硬件团队尚未给出最终的频率/强度/时长范围，因此这里使用占位值并加上 TODO 备注。
 * 后续若有新的取值限制，只需更新此处常量即可，界面与校验逻辑会自动复用。
 */
object CustomPresetConstraints {
    const val MIN_FREQUENCY_HZ = 0            // TODO: 向硬件确认最小频率
    const val MAX_FREQUENCY_HZ = 0            // TODO: 向硬件确认最大频率

    const val MIN_INTENSITY_01V = 0
    const val MAX_INTENSITY_01V = 255         // TODO: 根据硬件实际强度上限调整

    const val MIN_DURATION_SEC = 1
    const val MAX_DURATION_SEC = 0            // TODO: 向硬件确认允许的最长单步时长

    const val FREQUENCY_UNIT = "Hz"
    const val INTENSITY_UNIT = "0.01V"
    const val DURATION_UNIT = "秒"

    fun clampFrequency(value: Int): Int =
        if (MAX_FREQUENCY_HZ > MIN_FREQUENCY_HZ) {
            value.coerceIn(MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ)
        } else {
            value.coerceAtLeast(MIN_FREQUENCY_HZ)
        }

    fun clampIntensity(value: Int): Int =
        value.coerceIn(MIN_INTENSITY_01V, MAX_INTENSITY_01V)

    fun clampDuration(value: Int): Int =
        if (MAX_DURATION_SEC > MIN_DURATION_SEC) {
            value.coerceIn(MIN_DURATION_SEC, MAX_DURATION_SEC)
        } else {
            value.coerceAtLeast(MIN_DURATION_SEC)
        }
}
