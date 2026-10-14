package com.reellock.reellock

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.reellock.reellock.commitment.CommitmentEngine
import com.reellock.reellock.deviceadmin.DevicePolicyController
import com.reellock.reellock.enforcement.EnforcementManager
import com.reellock.reellock.monitoring.PermissionCheckWorker
import com.reellock.reellock.monitoring.PermissionMonitor
import com.reellock.reellock.persistence.AppDatabase
import com.reellock.reellock.recovery.DeviceIdentity
import com.reellock.reellock.recovery.RecoveryManager
import com.reellock.reellock.security.SecurityEventLogger
import com.reellock.reellock.time.TrustedTimeManager
import com.reellock.reellock.usage.UsageAccountingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Manual dependency container (deliberately no DI framework - the object
 * graph here is small and static for the app's lifetime). Everything the
 * bridge, the accessibility service and the boot receiver need is exposed
 * as a `val` here.
 */
class ReelLockApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.getInstance(this) }
    val trustedTimeManager by lazy { TrustedTimeManager(this) }
    val deviceIdentity by lazy { DeviceIdentity(this) }
    val devicePolicyController by lazy { DevicePolicyController(this) }
    val permissionMonitor by lazy { PermissionMonitor(this) }

    val securityEventLogger by lazy { SecurityEventLogger(database.securityEventDao(), trustedTimeManager) }

    val commitmentEngine by lazy {
        CommitmentEngine(
            activeCommitmentDao = database.activeCommitmentDao(),
            commitmentConfigDao = database.commitmentConfigDao(),
            trustedTimeManager = trustedTimeManager,
            securityEventLogger = securityEventLogger,
        )
    }

    val usageAccountingEngine by lazy {
        UsageAccountingEngine(
            bootSessionDao = database.bootSessionDao(),
            usageIntervalDao = database.usageIntervalDao(),
            trustedTimeManager = trustedTimeManager,
        )
    }

    val recoveryManager by lazy {
        RecoveryManager(
            recoveryRequestDao = database.recoveryRequestDao(),
            recoveryApprovalDao = database.recoveryApprovalDao(),
            activeCommitmentDao = database.activeCommitmentDao(),
            commitmentConfigDao = database.commitmentConfigDao(),
            commitmentEngine = commitmentEngine,
            deviceIdentity = deviceIdentity,
            trustedTimeManager = trustedTimeManager,
            securityEventLogger = securityEventLogger,
        )
    }

    val enforcementManager by lazy { EnforcementManager(commitmentEngine, usageAccountingEngine) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            usageAccountingEngine.ensureBootSession()
            usageAccountingEngine.reconcileOnStart()
        }
        schedulePermissionChecks()
    }

    private fun schedulePermissionChecks() {
        val request = PeriodicWorkRequestBuilder<PermissionCheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PermissionCheckWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
