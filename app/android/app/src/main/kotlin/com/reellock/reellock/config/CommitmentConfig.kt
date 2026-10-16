package com.reellock.reellock.config

import com.reellock.reellock.classifier.TargetApp

/**
 * User-editable rule set, per need.md section 4. Only ever mutable before
 * activation or after expiry/authorized recovery — CommitmentEngine is the
 * sole gate that enforces that, never the UI.
 *
 * [monitoredApps] (package names) replaced a single hardcoded targetPackage
 * once YouTube Shorts support was added alongside Instagram Reels - they
 * share one allowance/window (usageAccountingEngine has no notion of
 * "which app" at all), so this only controls which apps' screens actually
 * count against it and get redirected away from.
 */
data class CommitmentConfig(
    val version: Int,
    val reelsAllowanceSeconds: Long,
    val windowSeconds: Long,
    val commitmentDays: Int,
    val recoveryMode: RecoveryMode,
    val enforcementMode: EnforcementMode,
    val monitoredApps: Set<String> = setOf(TargetApp.INSTAGRAM.packageName),
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
            monitoredApps = setOf(TargetApp.INSTAGRAM.packageName),
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
