package com.reellock.reellock.recovery

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * A random, locally-generated device identifier used only to bind recovery
 * requests to this installation (so a signed approval cannot be replayed
 * against a different device). Not a hardware identifier, not used for
 * tracking, not sent anywhere unless a remote recovery backend is enabled.
 */
class DeviceIdentity(context: Context) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "reellock_device_identity",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun get(): String {
        prefs.getString(KEY, null)?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, generated).apply()
        return generated
    }

    companion object {
        private const val KEY = "device_id"
    }
}
