package com.reellock.reellock.monitoring

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reellock.reellock.ReelLockApplication
import com.reellock.reellock.commitment.SecurityEventType

/**
 * need.md R9: periodic WorkManager check for permission/service loss, since
 * Android has no callback for "the user just disabled accessibility for
 * this app". Only logs a SecurityEvent on a *transition* so the audit log
 * doesn't fill up with a duplicate row every 15 minutes.
 */
class PermissionCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ReelLockApplication
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val status = app.permissionMonitor.check()
        val wasEnabled = prefs.getBoolean(KEY_WAS_ENABLED, true)

        if (wasEnabled && !status.accessibilityServiceEnabled) {
            app.securityEventLogger.log(SecurityEventType.PERMISSION_LOST, outcome = "accessibility_disabled")
        } else if (!wasEnabled && status.accessibilityServiceEnabled) {
            app.securityEventLogger.log(SecurityEventType.PERMISSION_RESTORED, outcome = "accessibility_enabled")
        }

        prefs.edit().putBoolean(KEY_WAS_ENABLED, status.accessibilityServiceEnabled).apply()
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "reellock_permission_check"
        private const val PREFS_NAME = "reellock_permission_monitor"
        private const val KEY_WAS_ENABLED = "was_accessibility_enabled"
    }
}
