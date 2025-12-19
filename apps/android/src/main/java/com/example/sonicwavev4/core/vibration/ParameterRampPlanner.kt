package com.example.sonicwavev4.core.vibration

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

object ParameterRampPlanner {

    data class RampPlan(
        val tickMs: Int,
        val points: List<Pair<Int, Int>>
    )

    fun plan(
        startFreq: Int,
        startIntensity: Int,
        targetFreq: Int,
        targetIntensity: Int,
        spec: ParameterTransitionSpec
    ): RampPlan {
        val tickMs = spec.tickMs
        require(tickMs > 0) { "tickMs must be positive" }
        val steps = when (spec) {
            is ParameterTransitionSpec.DurationSpec -> {
                max(1, ceil(spec.durationMs.toDouble() / tickMs).toInt())
            }

            is ParameterTransitionSpec.StepsSpec -> max(1, spec.steps)
        }

        val freqPoints = distribute(startFreq, targetFreq, steps)
        val intensityPoints = distribute(startIntensity, targetIntensity, steps)
        val points = freqPoints.zip(intensityPoints)

        return RampPlan(tickMs = tickMs, points = points)
    }

    private fun distribute(start: Int, end: Int, steps: Int): List<Int> {
        require(steps >= 1)
        if (steps == 1) {
            return listOf(end)
        }

        val delta = end - start
        val stepSign = when {
            delta > 0 -> 1
            delta < 0 -> -1
            else -> 0
        }
        val numerator = abs(delta)
        val denominator = steps - 1

        val points = MutableList(steps) { start }
        var current = start
        var error = 0

        for (i in 1 until steps) {
            error += numerator
            while (error >= denominator && denominator != 0) {
                current += stepSign
                error -= denominator
            }
            points[i] = current
        }

        points[steps - 1] = end
        return points
    }
}
