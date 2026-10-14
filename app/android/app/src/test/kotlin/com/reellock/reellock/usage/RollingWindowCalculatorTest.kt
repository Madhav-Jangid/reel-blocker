package com.reellock.reellock.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class RollingWindowCalculatorTest {

    private val allowanceSeconds = 1_800L
    private val windowSeconds = 18_000L

    @Test
    fun `no intervals means full allowance remains`() {
        val result = RollingWindowCalculator.compute(emptyList(), trustedNowMillis = 100_000_000, windowSeconds, allowanceSeconds)
        assertEquals(1_800L, result.remainingSeconds)
        assertEquals(0L, result.usedSeconds)
    }

    @Test
    fun `interval fully inside the window counts fully`() {
        val now = 10_000_000L
        val interval = IntervalSample(wallStartMillis = now - 600_000, wallEndMillis = now - 300_000, isLowConfidence = false) // 300s
        val result = RollingWindowCalculator.compute(listOf(interval), now, windowSeconds, allowanceSeconds)
        assertEquals(300L, result.usedSeconds)
        assertEquals(1_500L, result.remainingSeconds)
    }

    @Test
    fun `interval entirely before the window is excluded`() {
        val now = 100_000_000L
        val windowStart = now - windowSeconds * 1000
        val interval = IntervalSample(wallStartMillis = windowStart - 10_000, wallEndMillis = windowStart - 5_000, isLowConfidence = false)
        val result = RollingWindowCalculator.compute(listOf(interval), now, windowSeconds, allowanceSeconds)
        assertEquals(0L, result.usedSeconds)
    }

    @Test
    fun `interval straddling the window boundary is clipped to the overlap`() {
        val now = 100_000_000L
        val windowStart = now - windowSeconds * 1000
        // Starts 10s before the window opens, ends 50s after it opens -> only 50s counts.
        val interval = IntervalSample(wallStartMillis = windowStart - 10_000, wallEndMillis = windowStart + 50_000, isLowConfidence = false)
        val result = RollingWindowCalculator.compute(listOf(interval), now, windowSeconds, allowanceSeconds)
        assertEquals(50L, result.usedSeconds)
    }

    @Test
    fun `an open interval counts up to now, not beyond`() {
        val now = 10_000_000L
        val interval = IntervalSample(wallStartMillis = now - 120_000, wallEndMillis = now + 999_999_999, isLowConfidence = false)
        val result = RollingWindowCalculator.compute(listOf(interval), now, windowSeconds, allowanceSeconds)
        assertEquals(120L, result.usedSeconds)
    }

    @Test
    fun `usage reaching the allowance exhausts the budget to zero, never negative`() {
        val now = 10_000_000L
        val interval = IntervalSample(wallStartMillis = now - 2_000_000, wallEndMillis = now, isLowConfidence = false) // 2000s > 1800s allowance
        val result = RollingWindowCalculator.compute(listOf(interval), now, windowSeconds, allowanceSeconds)
        assertEquals(0L, result.remainingSeconds)
    }

    @Test
    fun `multiple intervals sum correctly and low-confidence seconds are tracked separately`() {
        val now = 10_000_000L
        val highConfidence = IntervalSample(now - 500_000, now - 400_000, isLowConfidence = false) // 100s
        val lowConfidence = IntervalSample(now - 300_000, now - 250_000, isLowConfidence = true) // 50s
        val result = RollingWindowCalculator.compute(listOf(highConfidence, lowConfidence), now, windowSeconds, allowanceSeconds)
        assertEquals(150L, result.usedSeconds)
        assertEquals(50L, result.lowConfidenceSecondsCounted)
        assertEquals(1_650L, result.remainingSeconds)
    }
}
