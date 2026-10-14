package com.reellock.reellock.monitoring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * need.md section 9 "Boot recovery sequence" step 1: "Receive the boot event
 * where permitted and schedule recovery work." Does no work itself - just
 * enqueues BootReconciliationWorker, since BroadcastReceiver.onReceive has a
 * tight execution-time budget.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val request = OneTimeWorkRequestBuilder<BootReconciliationWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            BootReconciliationWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
