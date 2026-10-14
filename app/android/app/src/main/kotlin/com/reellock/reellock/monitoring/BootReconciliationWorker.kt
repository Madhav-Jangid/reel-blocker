package com.reellock.reellock.monitoring

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reellock.reellock.ReelLockApplication
import com.reellock.reellock.commitment.SecurityEventType

/** need.md section 9 "Boot recovery sequence" steps 2-6. */
class BootReconciliationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ReelLockApplication

        app.usageAccountingEngine.ensureBootSession()
        app.usageAccountingEngine.reconcileOnStart()

        val snapshot = app.commitmentEngine.getSnapshot()
        val permissionStatus = app.permissionMonitor.check()

        app.securityEventLogger.log(
            SecurityEventType.BOOT_RECONCILED,
            outcome = snapshot.status.name,
            configVersion = snapshot.config?.version,
            details = "accessibilityEnabled=${permissionStatus.accessibilityServiceEnabled}, clockAnomaly=${snapshot.clockAnomalyDetected}",
        )

        if (!permissionStatus.accessibilityServiceEnabled) {
            app.securityEventLogger.log(SecurityEventType.PERMISSION_LOST, outcome = "accessibility_disabled_after_boot")
        }

        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "reellock_boot_reconciliation"
    }
}
