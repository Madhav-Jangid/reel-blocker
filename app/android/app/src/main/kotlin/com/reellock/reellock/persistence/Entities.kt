package com.reellock.reellock.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved configuration. `isDraft = true` rows are editable working copies
 * (R1); the row referenced by ActiveCommitmentEntity.configVersion is the
 * immutable activation snapshot and must never be mutated in place.
 */
@Entity(tableName = "commitment_config")
data class CommitmentConfigEntity(
    @PrimaryKey(autoGenerate = true) val version: Int = 0,
    val reelsAllowanceSeconds: Long,
    val windowSeconds: Long,
    val commitmentDays: Int,
    val recoveryMode: String,
    val enforcementMode: String,
    /** Comma-joined package names - see CommitmentConfig.monitoredApps. */
    val monitoredApps: String,
    val blockUnknownScreens: Boolean,
    val allowRecoveryDuringLock: Boolean,
    val trustedPersonPublicKeyBase64: String?,
    val createdAtEpochMillis: Long,
    val isDraft: Boolean,
)

/**
 * Single-row table (id is always 0). Holds the CommitmentEngine state
 * machine's current node (need.md section 4 state diagram) and the
 * activation deadline. Never written to except by CommitmentEngine.
 */
@Entity(tableName = "active_commitment")
data class ActiveCommitmentEntity(
    @PrimaryKey val id: Int = 0,
    val configVersion: Int,
    val activatedAtEpochMillis: Long,
    val deadlineEpochMillis: Long,
    val status: String,
    val authorizedRecovery: Boolean,
)

/** Trusted-time anchor established at each process/boot start. See TrustedTimeManager. */
@Entity(tableName = "boot_session")
data class BootSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val anchorWallClockMillis: Long,
    val anchorElapsedRealtimeMillis: Long,
    val createdAtEpochMillis: Long,
)

/**
 * One recorded Reels-foreground interval. `endElapsedRealtimeMillis == null`
 * means the session is still open (or was left open by a process death and
 * needs reconciliation on next start, see UsageAccountingEngine.reconcile).
 */
@Entity(tableName = "usage_interval")
data class UsageIntervalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bootSessionId: Long,
    val startElapsedRealtimeMillis: Long,
    val endElapsedRealtimeMillis: Long?,
    /** Updated periodically while the interval is open; used to cap reconciliation after a process death/reboot instead of extending the session to "now". */
    val lastHeartbeatElapsedRealtimeMillis: Long,
    val confidence: String,
    val closedReason: String?,
)

@Entity(tableName = "recovery_request")
data class RecoveryRequestEntity(
    @PrimaryKey val nonce: String,
    val reason: String,
    val requestedAction: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val commitmentVersion: Int,
    val deviceId: String,
    val status: String,
)

@Entity(tableName = "recovery_approval")
data class RecoveryApprovalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestNonce: String,
    val approverPublicKeyBase64: String,
    val signatureBase64: String,
    val approvedAtEpochMillis: Long,
    val used: Boolean,
)

@Entity(tableName = "security_event")
data class SecurityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val timestampEpochMillis: Long,
    val configVersion: Int?,
    val outcome: String,
    val details: String?,
)
