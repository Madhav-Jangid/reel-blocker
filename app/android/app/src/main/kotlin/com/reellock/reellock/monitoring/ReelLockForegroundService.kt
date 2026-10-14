package com.reellock.reellock.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.reellock.reellock.MainActivity
import com.reellock.reellock.R

/**
 * need.md section 10: "Foreground service, where justified | Maintain
 * user-visible monitoring on supported Android versions."
 *
 * Why this exists: InstagramAccessibilityService alone is not enough to
 * survive process death. Android (MIUI especially, but not only MIUI) kills
 * an app's entire process - including any bound accessibility service
 * running in it - once its last Activity task is swiped from Recents,
 * unless something is keeping that process alive. A foreground service with
 * a visible notification is the standard, documented way to do that; it was
 * missing before, which is why usage tracking stopped dead the moment the
 * app was swiped away, no matter how much Reels was watched afterward.
 *
 * This is still not an absolute guarantee (need.md section 9: "Android may
 * delay background execution" is listed as a remaining limitation even with
 * this in place) - some OEMs (MIUI very much included) have an additional,
 * separate "autostart" / battery-optimization kill switch that can still
 * terminate a foreground service's process. See README for the MIUI-specific
 * settings to check.
 */
class ReelLockForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        createChannelIfNeeded()

        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent ?: Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ReelLock is active")
            .setContentText("Watching for Instagram Reels to enforce your commitment.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ReelLock monitoring",
            NotificationManager.IMPORTANCE_LOW, // no sound/heads-up - this is a status indicator, not an alert
        )
        channel.description = "Shows while ReelLock is actively watching for Reels usage."
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "reellock_monitoring"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, ReelLockForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ReelLockForegroundService::class.java))
        }
    }
}
