package com.reellock.reellock.deviceadmin

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.UserManager

data class ManagedModeStatus(
    val isDeviceOwner: Boolean,
    val uninstallBlocked: Boolean,
    val safeBootDisallowed: Boolean,
)

/**
 * need.md section 8: "Managed mode" vs "Standard mode". Every method here is
 * a no-op (returns false / does nothing) unless this app is genuinely
 * provisioned as device owner - it never claims protection it cannot
 * actually apply (R9). Device-owner provisioning itself is NOT done by this
 * app; it is an out-of-band step (see README "Managed mode setup").
 */
class DevicePolicyController(private val context: Context) {

    private val dpm: DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    private val adminComponent = android.content.ComponentName(context, ReelLockDeviceAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean = dpm?.isDeviceOwnerApp(context.packageName) ?: false

    /** Blocks uninstalling ReelLock itself. Only effective for a device owner. */
    fun setUninstallBlocked(blocked: Boolean): Boolean {
        val manager = dpm ?: return false
        if (!isDeviceOwner()) return false
        return try {
            manager.setUninstallBlocked(adminComponent, context.packageName, blocked)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** need.md "restrictions on ... entering Safe Mode where supported." */
    fun setSafeBootDisallowed(disallowed: Boolean): Boolean {
        val manager = dpm ?: return false
        if (!isDeviceOwner()) return false
        return try {
            if (disallowed) {
                manager.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            } else {
                manager.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            }
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun applyManagedModePolicies(): ManagedModeStatus {
        if (!isDeviceOwner()) {
            return ManagedModeStatus(isDeviceOwner = false, uninstallBlocked = false, safeBootDisallowed = false)
        }
        val uninstallBlocked = setUninstallBlocked(true)
        val safeBootDisallowed = setSafeBootDisallowed(true)
        return ManagedModeStatus(isDeviceOwner = true, uninstallBlocked = uninstallBlocked, safeBootDisallowed = safeBootDisallowed)
    }

    fun currentStatus(): ManagedModeStatus {
        val owner = isDeviceOwner()
        if (!owner) return ManagedModeStatus(isDeviceOwner = false, uninstallBlocked = false, safeBootDisallowed = false)
        val manager = dpm ?: return ManagedModeStatus(isDeviceOwner = true, uninstallBlocked = false, safeBootDisallowed = false)
        val uninstallBlocked = try {
            manager.isUninstallBlocked(adminComponent, context.packageName)
        } catch (e: SecurityException) {
            false
        }
        val safeBootDisallowed = try {
            manager.getUserRestrictions(adminComponent).getBoolean(UserManager.DISALLOW_SAFE_BOOT, false)
        } catch (e: SecurityException) {
            false
        }
        return ManagedModeStatus(isDeviceOwner = true, uninstallBlocked = uninstallBlocked, safeBootDisallowed = safeBootDisallowed)
    }
}
