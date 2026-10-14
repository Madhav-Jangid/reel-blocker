package com.reellock.reellock.bridge

import com.reellock.reellock.commitment.CommitmentSnapshot
import com.reellock.reellock.config.CommitmentConfig
import com.reellock.reellock.deviceadmin.ManagedModeStatus
import com.reellock.reellock.monitoring.PermissionStatus
import com.reellock.reellock.persistence.SecurityEventEntity
import com.reellock.reellock.usage.RemainingBudget

/** Manual, dependency-free (de)serialization between domain types and the Flutter MethodChannel's `Map<String, Any?>` wire format. */

fun CommitmentConfig.toMap(): Map<String, Any?> = mapOf(
    "version" to version,
    "reelsAllowanceSeconds" to reelsAllowanceSeconds,
    "windowSeconds" to windowSeconds,
    "commitmentDays" to commitmentDays,
    "recoveryMode" to recoveryMode.name,
    "enforcementMode" to enforcementMode.name,
    "targetPackage" to targetPackage,
    "blockUnknownScreens" to blockUnknownScreens,
    "allowRecoveryDuringLock" to allowRecoveryDuringLock,
)

fun configFromMap(map: Map<String, Any?>): CommitmentConfig = CommitmentConfig(
    version = (map["version"] as? Number)?.toInt() ?: 0,
    reelsAllowanceSeconds = (map["reelsAllowanceSeconds"] as Number).toLong(),
    windowSeconds = (map["windowSeconds"] as Number).toLong(),
    commitmentDays = (map["commitmentDays"] as Number).toInt(),
    recoveryMode = com.reellock.reellock.config.RecoveryMode.valueOf(map["recoveryMode"] as String),
    enforcementMode = com.reellock.reellock.config.EnforcementMode.valueOf(map["enforcementMode"] as String),
    targetPackage = (map["targetPackage"] as? String) ?: "com.instagram.android",
    blockUnknownScreens = (map["blockUnknownScreens"] as? Boolean) ?: false,
    allowRecoveryDuringLock = (map["allowRecoveryDuringLock"] as? Boolean) ?: true,
)

fun CommitmentSnapshot.toMap(): Map<String, Any?> = mapOf(
    "status" to status.name,
    "config" to config?.toMap(),
    "activatedAtEpochMillis" to activatedAtEpochMillis,
    "deadlineEpochMillis" to deadlineEpochMillis,
    "nowEpochMillis" to nowEpochMillis,
    "authorizedRecovery" to authorizedRecovery,
    "clockAnomalyDetected" to clockAnomalyDetected,
)

fun RemainingBudget.toMap(): Map<String, Any?> = mapOf(
    "remainingSeconds" to remainingSeconds,
    "usedSeconds" to usedSeconds,
    "allowanceSeconds" to allowanceSeconds,
    "windowSeconds" to windowSeconds,
    "lowConfidenceSecondsCounted" to lowConfidenceSecondsCounted,
)

fun PermissionStatus.toMap(): Map<String, Any?> = mapOf(
    "accessibilityServiceEnabled" to accessibilityServiceEnabled,
    "notificationsEnabled" to notificationsEnabled,
    "batteryOptimizationIgnored" to batteryOptimizationIgnored,
    "isDeviceOwner" to isDeviceOwner,
    "isMiui" to isMiui,
    "protectionLevel" to protectionLevel.name,
)

fun ManagedModeStatus.toMap(): Map<String, Any?> = mapOf(
    "isDeviceOwner" to isDeviceOwner,
    "uninstallBlocked" to uninstallBlocked,
    "safeBootDisallowed" to safeBootDisallowed,
)

fun SecurityEventEntity.toMap(): Map<String, Any?> = mapOf(
    "type" to type,
    "timestampEpochMillis" to timestampEpochMillis,
    "configVersion" to configVersion,
    "outcome" to outcome,
    "details" to details,
)
