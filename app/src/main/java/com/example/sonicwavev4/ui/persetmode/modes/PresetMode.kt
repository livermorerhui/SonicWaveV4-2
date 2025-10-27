package com.example.sonicwavev4.ui.persetmode.modes

data class Step(
    val intensity01V: Int,  // 强度: 0.01V
    val frequencyHz: Int,   // 频率: Hz
    val durationSec: Int    // 时间: s
)

interface PresetMode {
    val id: String
    val displayName: String
    val steps: List<Step>
    val totalDurationSec: Int get() = steps.sumOf { it.durationSec }
    fun scaled(factor: Double): List<Step> =
        steps.map { it.copy(intensity01V = (it.intensity01V * factor).toInt().coerceIn(0, 200)) }
}

/** 统一的 CSV 解析：每行形如  intensity,frequency,duration ；允许空行和 # 注释 */
fun parseStepsCsv(csv: String): List<Step> =
    csv.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line ->
            val p = line.split(',').map { it.trim() }
            require(p.size == 3) { "CSV 行必须为3列: 强度,频率,时长。出错行: $line" }
            Step(p[0].toInt(), p[1].toInt(), p[2].toInt())
        }.toList()
