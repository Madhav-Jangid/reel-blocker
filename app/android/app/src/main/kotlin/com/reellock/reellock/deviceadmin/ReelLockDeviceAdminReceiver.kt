package com.reellock.reellock.deviceadmin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * need.md section 8. Being enabled as a plain device admin grants nothing by
 * itself - DevicePolicyController always checks isDeviceOwnerApp() before
 * relying on any restricted API. Device-owner status can only come from
 * provisioning (QR/NFC/`dpm set-device-owner` during initial setup), never
 * from this receiver alone.
 */
class ReelLockDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
