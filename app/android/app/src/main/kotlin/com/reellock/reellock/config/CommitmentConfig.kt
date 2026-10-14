package com.reellock.reellock.config

/**
 * User-editable rule set, per need.md section 4. Only ever mutable before
 * activation or after expiry/authorized recovery — CommitmentEngine is the
 * sole gate that enforces that, never the UI.
 */
data class CommitmentConfig(
    val version: Int,
    val reelsAllowanceSeconds: Long,
    val windowSeconds: Long,
    val commitmentDays: Int,
    val recoveryMode: RecoveryMode,
    val enforcementMode: EnforcementMode,
    val targetPackage: String = "com.instagram.android",
    val blockUnknownScreens: Boolean = false,
    val allowRecoveryDuringLock: Boolean = true,
) {
    companion object {
        val DEFAULT = CommitmentConfig(
            version = 1,
            reelsAllowanceSeconds = 1_800,
            windowSeconds = 18_000,
            commitmentDays = 30,
            recoveryMode = RecoveryMode.TRUSTED_PERSON,
            enforcementMode = EnforcementMode.ACCESSIBILITY,
            blockUnknownScreens = false,
            allowRecoveryDuringLock = true,
        )
    }
}

enum class RecoveryMode {
    TRUSTED_PERSON,
}

enum class EnforcementMode {
    ACCESSIBILITY,
    DEVICE_MANAGED,
}
