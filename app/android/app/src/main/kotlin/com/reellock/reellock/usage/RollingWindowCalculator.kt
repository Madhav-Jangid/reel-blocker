package com.reellock.reellock.usage

/** Plain-data input to the rolling-window calculation; decoupled from Room so the math is unit-testable on the plain JVM. */
data class IntervalSample(
    val wallStartMillis: Long,
    val wallEndMillis: Long,
    val isLowConfidence: Boolean,
)

/**
 * need.md section 5 "Usage accounting algorithm", extracted as a pure
 * function: remaining(t) = max(0, allowance - usage(t-W, t)).
 */
object RollingWindowCalculator {
    fun compute(
        intervals: List<IntervalSample>,
        trustedNowMillis: Long,
        windowSeconds: Long,
        allowanceSeconds: Long,
    ): RemainingBudget {
        val windowStart = trustedNowMillis - windowSeconds * 1000L
        var usedMillis = 0L
        var lowConfidenceMillis = 0L

        for (interval in intervals) {
            val overlapStart = maxOf(interval.wallStartMillis, windowStart)
            val overlapEnd = minOf(interval.wallEndMillis, trustedNowMillis)
            val overlap = (overlapEnd - overlapStart).coerceAtLeast(0)
            usedMillis += overlap
            if (interval.isLowConfidence) lowConfidenceMillis += overlap
        }

        val usedSeconds = usedMillis / 1000
        val remaining = (allowanceSeconds - usedSeconds).coerceAtLeast(0)
        return RemainingBudget(
            remainingSeconds = remaining,
            usedSeconds = usedSeconds,
            allowanceSeconds = allowanceSeconds,
            windowSeconds = windowSeconds,
            lowConfidenceSecondsCounted = lowConfidenceMillis / 1000,
        )
    }
}
