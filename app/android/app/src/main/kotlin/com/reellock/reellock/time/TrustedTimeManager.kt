package com.reellock.reellock.time

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * need.md section 6, "Timing and clock manipulation": elapsedRealtime() is
 * monotonic but resets across reboots; currentTimeMillis() survives reboots
 * but the user can change it. This class produces a single "trusted now"
 * that:
 *  - tracks currentTimeMillis() when it moves consistently with elapsedRealtime
 *    (so legitimate passage of time, including deep sleep and NTP drift, is
 *    reflected correctly), and
 *  - falls back to an elapsedRealtime-anchored estimate - never moving
 *    backward, and never jumping forward faster than real time - whenever the
 *    wall clock disagrees with elapsed time by more than [TOLERANCE_MILLIS].
 *
 * This is explicitly NOT a cryptographic trusted-time source (need.md is
 * explicit that a fully offline app cannot robustly rule out every clock
 * manipulation). It is a conservative monotonic-floor heuristic: it stops a
 * naive "set the clock back/forward" attack from resetting usage or the
 * commitment deadline, at the cost of being unable to distinguish that from
 * some legitimate manual clock corrections. Callers should treat
 * [TrustedNowResult.anomalyDetected] as a signal to surface to the user /
 * audit log (R9), not to silently "fix" anything beyond capping the estimate.
 */
class TrustedTimeManager(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "reellock_trusted_time",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    data class TrustedNowResult(
        val nowEpochMillis: Long,
        val anomalyDetected: Boolean,
        val anomalyMagnitudeMillis: Long,
    )

    /** Advances and returns the trusted-now estimate, persisting the new anchor. Call at most once per use site per evaluation. */
    fun now(): TrustedNowResult {
        val elapsedRealtimeNow = SystemClock.elapsedRealtime()
        val wallClockNow = System.currentTimeMillis()

        val anchorWall = prefs.getLong(KEY_ANCHOR_WALL, -1L)
        val anchorElapsed = prefs.getLong(KEY_ANCHOR_ELAPSED, -1L)

        if (anchorWall < 0 || anchorElapsed < 0) {
            // First run ever: bootstrap trust from the wall clock. Documented
            // limitation: a device whose clock is already wrong before first
            // launch cannot be detected from this point alone.
            persistAnchor(wallClockNow, elapsedRealtimeNow)
            return TrustedNowResult(wallClockNow, anomalyDetected = false, anomalyMagnitudeMillis = 0)
        }

        val elapsedDeltaSinceAnchor = elapsedRealtimeNow - anchorElapsed
        val monotonicEstimate = anchorWall + elapsedDeltaSinceAnchor
        val diff = wallClockNow - monotonicEstimate

        val accepted: Long
        val anomaly: Boolean
        if (kotlin.math.abs(diff) > TOLERANCE_MILLIS) {
            // Wall clock disagrees with the passage of monotonic time by more
            // than the tolerance: treat the monotonic estimate as the trusted
            // value rather than trusting an operator-editable clock jump.
            accepted = monotonicEstimate
            anomaly = true
        } else {
            accepted = wallClockNow
            anomaly = false
        }

        persistAnchor(accepted, elapsedRealtimeNow)
        return TrustedNowResult(accepted, anomaly, kotlin.math.abs(diff))
    }

    private fun persistAnchor(wall: Long, elapsed: Long) {
        prefs.edit()
            .putLong(KEY_ANCHOR_WALL, wall)
            .putLong(KEY_ANCHOR_ELAPSED, elapsed)
            .apply()
    }

    companion object {
        private const val KEY_ANCHOR_WALL = "anchor_wall_clock_millis"
        private const val KEY_ANCHOR_ELAPSED = "anchor_elapsed_realtime_millis"

        /** Tolerates ordinary NTP/timezone drift and the time this check itself takes; beyond this is treated as manual tampering. */
        const val TOLERANCE_MILLIS = 2 * 60 * 1000L
    }
}
