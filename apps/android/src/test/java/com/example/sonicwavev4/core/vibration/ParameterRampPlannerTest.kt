package com.example.sonicwavev4.core.vibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParameterRampPlannerTest {

    @Test
    fun `delta zero produces constant points`() {
        val spec = ParameterTransitionSpec.StepsSpec(steps = 3, tickMs = 10)

        val plan = ParameterRampPlanner.plan(
            startFreq = 120,
            startIntensity = 70,
            targetFreq = 120,
            targetIntensity = 70,
            spec = spec
        )

        assertEquals(3, plan.points.size)
        assertEquals(10, plan.tickMs)
        assertTrue(plan.points.all { it.first == 120 && it.second == 70 })
        assertEquals(120 to 70, plan.points.last())
    }

    @Test
    fun `positive delta is monotonic and ends at target`() {
        val spec = ParameterTransitionSpec.StepsSpec(steps = 6, tickMs = 20)

        val plan = ParameterRampPlanner.plan(
            startFreq = 0,
            startIntensity = 0,
            targetFreq = 10,
            targetIntensity = 5,
            spec = spec
        )

        assertEquals(6, plan.points.size)
        assertEquals(10 to 5, plan.points.last())
        assertMonotonic(plan.points.map { it.first }, delta = 10)
        assertMonotonic(plan.points.map { it.second }, delta = 5)
    }

    @Test
    fun `negative delta is monotonic and ends at target`() {
        val spec = ParameterTransitionSpec.StepsSpec(steps = 4, tickMs = 30)

        val plan = ParameterRampPlanner.plan(
            startFreq = 10,
            startIntensity = 8,
            targetFreq = 4,
            targetIntensity = 2,
            spec = spec
        )

        assertEquals(4, plan.points.size)
        assertEquals(4 to 2, plan.points.last())
        assertMonotonic(plan.points.map { it.first }, delta = -6)
        assertMonotonic(plan.points.map { it.second }, delta = -6)
    }

    @Test
    fun `large intensity delta diffuses error but remains monotonic`() {
        val spec = ParameterTransitionSpec.DurationSpec(durationMs = 25, tickMs = 10)

        val plan = ParameterRampPlanner.plan(
            startFreq = 3,
            startIntensity = 0,
            targetFreq = 6,
            targetIntensity = 100,
            spec = spec
        )

        assertEquals(3, plan.points.size)
        assertEquals(6 to 100, plan.points.last())
        assertMonotonic(plan.points.map { it.first }, delta = 3)
        assertMonotonic(plan.points.map { it.second }, delta = 100)
    }

    private fun assertMonotonic(values: List<Int>, delta: Int) {
        if (delta >= 0) {
            assertTrue(values.zipWithNext().all { (a, b) -> a <= b })
        } else {
            assertTrue(values.zipWithNext().all { (a, b) -> a >= b })
        }
    }
}
