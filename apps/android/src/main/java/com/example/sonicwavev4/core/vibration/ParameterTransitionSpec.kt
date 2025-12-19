package com.example.sonicwavev4.core.vibration

sealed interface ParameterTransitionSpec {
    val tickMs: Int

    data class DurationSpec(
        val durationMs: Int,
        override val tickMs: Int
    ) : ParameterTransitionSpec

    data class StepsSpec(
        val steps: Int,
        override val tickMs: Int
    ) : ParameterTransitionSpec
}
