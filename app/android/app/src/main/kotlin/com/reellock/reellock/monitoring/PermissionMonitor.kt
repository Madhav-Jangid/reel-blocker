package com.reellock.reellock.monitoring

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.reellock.reellock.accessibility.InstagramAccessibilityService
import com.reellock.reellock.deviceadmin.DevicePolicyController

enum class ProtectionLevel {
    /** Accessibility detection active (and, if device-owner, managed-mode policies too). */
    ACTIVE,
    /** Known to be reduced - e.g. accessibility disabled - and the user has been told. */
    DEGRADED,
}

data class PermissionStatus(
    val accessibilityServiceEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val isDeviceOwner: Boolean,
    val isMiui: Boolean,
    val protectionLevel: ProtectionLevel,
)

/**
 * need.md R9 / section 3: "The service can be disabled in system settings,
 * so permission monitoring is essential." This can only observe current
 * state when asked (from the Flutter UI, or a periodic WorkManager check) -
 * there is no system callback for "a user turned accessibility off", by
 * design on Android.
 */
class PermissionMonitor(private val context: Context) {

    private val devicePolicyController = DevicePolicyController(context)

    fun check(): PermissionStatus {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val notificationsEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        val isDeviceOwner = devicePolicyController.isDeviceOwner()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        val isMiui = Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)

        val protection = if (accessibilityEnabled) ProtectionLevel.ACTIVE else ProtectionLevel.DEGRADED

        return PermissionStatus(
            accessibilityServiceEnabled = accessibilityEnabled,
            notificationsEnabled = notificationsEnabled,
            batteryOptimizationIgnored = batteryOptimizationIgnored,
            isDeviceOwner = isDeviceOwner,
            isMiui = isMiui,
            protectionLevel = protection,
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(context, InstagramAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServicesSetting)
        while (splitter.hasNext()) {
            val candidate = ComponentName.unflattenFromString(splitter.next())
            if (candidate == expected) return true
        }
        return false
    }
}
