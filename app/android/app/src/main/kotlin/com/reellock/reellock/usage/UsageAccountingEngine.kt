package com.reellock.reellock.usage

import android.os.SystemClock
import com.reellock.reellock.commitment.Confidence
import com.reellock.reellock.persistence.BootSessionDao
import com.reellock.reellock.persistence.BootSessionEntity
import com.reellock.reellock.persistence.UsageIntervalDao
import com.reellock.reellock.persistence.UsageIntervalEntity
import com.reellock.reellock.persistence.UsageIntervalWithAnchor
import com.reellock.reellock.time.TrustedTimeManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RemainingBudget(
    val remainingSeconds: Long,
    val usedSeconds: Long,
    val allowanceSeconds: Long,
    val windowSeconds: Long,
    val lowConfidenceSecondsCounted: Long,
)

/**
 * need.md section 5, "Usage accounting algorithm". Tracks actual Reels-
 * foreground intervals and computes remaining(t) = max(0, allowance -
 * usage(t-W, t)) over a rolling window. Durations are measured with
 * SystemClock.elapsedRealtime() (monotonic, immune to wall-clock edits);
 * window placement uses TrustedTimeManager's trusted-now estimate (immune to
 * naive clock rollback/advance, see its docs for the limits of that).
 */
class UsageAccountingEngine(
    private val bootSessionDao: BootSessionDao,
    private val usageIntervalDao: UsageIntervalDao,
    private val trustedTimeManager: TrustedTimeManager,
) {
    private val mutex = Mutex()

    @Volatile
    private var currentBootSessionId: Long = -1L

    /** Call once per process start, before any interval tracking. Idempotent within a process lifetime. */
    suspend fun ensureBootSession(): Long = mutex.withLock {
        currentBootSessionId.takeIf { it >= 0 }?.let { return@withLock it }

        val trusted = trustedTimeManager.now()
        val elapsed = SystemClock.elapsedRealtime()
        val id = bootSessionDao.insert(
            BootSessionEntity(
                anchorWallClockMillis = trusted.nowEpochMillis,
                anchorElapsedRealtimeMillis = elapsed,
                createdAtEpochMillis = trusted.nowEpochMillis,
            ),
        )
        currentBootSessionId = id
        id
    }

    /** Starts a new open interval. No-op (just heartbeats) if one is already open. */
    suspend fun startReelsSession(confidence: Confidence) = mutex.withLock {
        val bootId = ensureBootSessionLocked()
        val open = usageIntervalDao.getOpenInterval()
        if (open != null) {
            usageIntervalDao.update(open.copy(lastHeartbeatElapsedRealtimeMillis = SystemClock.elapsedRealtime()))
            return@withLock
        }
        val now = SystemClock.elapsedRealtime()
        usageIntervalDao.insert(
            UsageIntervalEntity(
                bootSessionId = bootId,
                startElapsedRealtimeMillis = now,
                endElapsedRealtimeMillis = null,
                lastHeartbeatElapsedRealtimeMillis = now,
                confidence = confidence.name,
                closedReason = null,
            ),
        )
    }

    /** Call periodically (e.g. on each accessibility event) while a Reels screen stays in the foreground. */
    suspend fun heartbeat() = mutex.withLock {
        val open = usageIntervalDao.getOpenInterval() ?: return@withLock
        usageIntervalDao.update(open.copy(lastHeartbeatElapsedRealtimeMillis = SystemClock.elapsedRealtime()))
    }

    /** need.md: "Stop the interval when the screen changes, Instagram leaves the foreground, or the device locks." */
    suspend fun endReelsSession(reason: String) = mutex.withLock {
        val open = usageIntervalDao.getOpenInterval() ?: return@withLock
        val now = SystemClock.elapsedRealtime()
        usageIntervalDao.update(
            open.copy(
                endElapsedRealtimeMillis = now,
                lastHeartbeatElapsedRealtimeMillis = now,
                closedReason = reason,
            ),
        )
    }

    /**
     * Closes any interval left open by a process death or reboot, capping its
     * end at the last recorded heartbeat rather than "now" - so downtime is
     * never counted as viewing (need.md: "Reconcile an unfinished session on
     * restart", "without counting the entire downtime as viewing").
     */
    suspend fun reconcileOnStart(): Boolean = mutex.withLock {
        val open = usageIntervalDao.getOpenInterval() ?: return@withLock false
        val bootId = ensureBootSessionLocked()
        val closingAt = if (open.bootSessionId == bootId) {
            // Same boot, process just died: elapsed clock is still valid, cap at last heartbeat.
            maxOf(open.lastHeartbeatElapsedRealtimeMillis, open.startElapsedRealtimeMillis)
        } else {
            // Reboot happened: old boot's elapsedRealtime clock is gone. The
            // interval's own stored heartbeat (on the OLD clock) is the most
            // conservative true end we have; it is never reinterpreted on the
            // new clock, so it cannot inflate usage.
            open.lastHeartbeatElapsedRealtimeMillis
        }
        usageIntervalDao.update(
            open.copy(
                endElapsedRealtimeMillis = closingAt,
                closedReason = "RECONCILED_${if (open.bootSessionId == bootId) "PROCESS_DEATH" else "REBOOT"}",
            ),
        )
        true
    }

    suspend fun getRemainingBudget(allowanceSeconds: Long, windowSeconds: Long): RemainingBudget {
        val trustedNow = trustedTimeManager.now().nowEpochMillis
        val candidates = usageIntervalDao.getIntervalsStartingBefore(trustedNow)

        val samples = candidates.map { interval ->
            val wallStart = interval.anchorWallClockMillis + (interval.startElapsedRealtimeMillis - interval.anchorElapsedRealtimeMillis)
            val wallEnd = if (interval.endElapsedRealtimeMillis != null) {
                interval.anchorWallClockMillis + (interval.endElapsedRealtimeMillis - interval.anchorElapsedRealtimeMillis)
            } else {
                trustedNow // still open: counts up to the current moment
            }
            IntervalSample(wallStart, wallEnd, interval.confidence == Confidence.LOW.name)
        }

        return RollingWindowCalculator.compute(samples, trustedNow, windowSeconds, allowanceSeconds)
    }

    private suspend fun ensureBootSessionLocked(): Long {
        currentBootSessionId.takeIf { it >= 0 }?.let { return it }
        val trusted = trustedTimeManager.now()
        val elapsed = SystemClock.elapsedRealtime()
        val id = bootSessionDao.insert(
            BootSessionEntity(
                anchorWallClockMillis = trusted.nowEpochMillis,
                anchorElapsedRealtimeMillis = elapsed,
                createdAtEpochMillis = trusted.nowEpochMillis,
            ),
        )
        currentBootSessionId = id
        return id
    }
}
