package com.reellock.reellock.commitment

import android.util.Log
import com.reellock.reellock.config.CommitmentConfig
import com.reellock.reellock.config.ConfigValidator
import com.reellock.reellock.config.EnforcementMode
import com.reellock.reellock.config.RecoveryMode
import com.reellock.reellock.persistence.ActiveCommitmentDao
import com.reellock.reellock.persistence.ActiveCommitmentEntity
import com.reellock.reellock.persistence.CommitmentConfigDao
import com.reellock.reellock.persistence.CommitmentConfigEntity
import com.reellock.reellock.security.SecurityEventLogger
import com.reellock.reellock.time.TrustedTimeManager
import java.util.Date

sealed class UpdateResult {
    object NoActiveCommitment : UpdateResult()
    object Locked : UpdateResult()
    data class Invalid(val errors: List<String>) : UpdateResult()
    data class Updated(val version: Int) : UpdateResult()
}

sealed class ActivationResult {
    data class Activated(val configVersion: Int, val deadlineEpochMillis: Long) : ActivationResult()
    object NoDraft : ActivationResult()
    data class Invalid(val errors: List<String>) : ActivationResult()
}

data class CommitmentSnapshot(
    val status: CommitmentStatus,
    val config: CommitmentConfig?,
    val activatedAtEpochMillis: Long?,
    val deadlineEpochMillis: Long?,
    val nowEpochMillis: Long,
    val authorizedRecovery: Boolean,
    val clockAnomalyDetected: Boolean,
)

/**
 * need.md section 6: "the commitment engine, not just whether the UI is
 * editable" is the single gate for every configuration mutation. The UI
 * (Flutter, via the bridge) never decides lock state itself - it only
 * reflects what this class returns.
 *
 * This is explicitly NOT a tamper-proof boundary against a compromised
 * device / rooted OS (need.md section 6: "a malicious modification of a
 * normal app's process or local storage could bypass checks implemented
 * only in that app"). It is correct against the app's own UI and against
 * ordinary user interaction with it.
 */
class CommitmentEngine(
    private val activeCommitmentDao: ActiveCommitmentDao,
    private val commitmentConfigDao: CommitmentConfigDao,
    private val trustedTimeManager: TrustedTimeManager,
    private val securityEventLogger: SecurityEventLogger,
) {

    suspend fun getSnapshot(): CommitmentSnapshot {
        reconcileExpiry()
        val active = activeCommitmentDao.get()
        val trusted = trustedTimeManager.now()
        if (active == null) {
            val draft = commitmentConfigDao.getLatestDraft()
            return CommitmentSnapshot(
                status = CommitmentStatus.SETUP,
                config = draft?.toConfig(),
                activatedAtEpochMillis = null,
                deadlineEpochMillis = null,
                nowEpochMillis = trusted.nowEpochMillis,
                authorizedRecovery = false,
                clockAnomalyDetected = trusted.anomalyDetected,
            )
        }
        val config = commitmentConfigDao.getByVersion(active.configVersion)
        return CommitmentSnapshot(
            status = CommitmentStatus.valueOf(active.status),
            config = config?.toConfig(),
            activatedAtEpochMillis = active.activatedAtEpochMillis,
            deadlineEpochMillis = active.deadlineEpochMillis,
            nowEpochMillis = trusted.nowEpochMillis,
            authorizedRecovery = active.authorizedRecovery,
            clockAnomalyDetected = trusted.anomalyDetected,
        )
    }

    /** R1: validates and stages a config. Rejected outright if currently locked. */
    suspend fun saveDraft(requested: CommitmentConfig, trustedPersonPublicKeyBase64: String? = null): UpdateResult {
        when (val validation = ConfigValidator.validate(requested)) {
            is ConfigValidator.ValidationResult.Invalid -> return UpdateResult.Invalid(validation.errors)
            ConfigValidator.ValidationResult.Valid -> Unit
        }

        reconcileExpiry()
        val active = activeCommitmentDao.get()
        if (active != null) {
            val status = CommitmentStatus.valueOf(active.status)
            val editable = status == CommitmentStatus.SETUP ||
                status == CommitmentStatus.EXPIRED ||
                (status == CommitmentStatus.RECOVERED && active.authorizedRecovery)
            if (!editable) {
                Log.d(TAG, "Config edit rejected: commitment is locked (status=$status)")
                securityEventLogger.log(SecurityEventType.UPDATE_REJECTED_LOCKED, outcome = "rejected", configVersion = active.configVersion)
                return UpdateResult.Locked
            }
        }

        val existingDraft = commitmentConfigDao.getLatestDraft()
        val version = (existingDraft?.version ?: 0) + 1
        val keyToPersist = trustedPersonPublicKeyBase64 ?: existingDraft?.trustedPersonPublicKeyBase64
        val entity = requested.toEntity(version = version, isDraft = true, trustedPersonPublicKeyBase64 = keyToPersist)
        if (existingDraft != null) {
            commitmentConfigDao.update(entity.copy(version = existingDraft.version))
            return UpdateResult.Updated(existingDraft.version)
        }
        val newId = commitmentConfigDao.insert(entity)
        return UpdateResult.Updated(newId.toInt())
    }

    /** need.md section 6 configuration lifecycle steps 3-5: explicit confirmation -> immutable snapshot -> lock engaged. */
    suspend fun activate(): ActivationResult {
        reconcileExpiry()
        val draft = commitmentConfigDao.getLatestDraft() ?: return ActivationResult.NoDraft
        val config = draft.toConfig()
        when (val validation = ConfigValidator.validate(config)) {
            is ConfigValidator.ValidationResult.Invalid -> return ActivationResult.Invalid(validation.errors)
            ConfigValidator.ValidationResult.Valid -> Unit
        }

        val trusted = trustedTimeManager.now()
        val deadline = trusted.nowEpochMillis + config.commitmentDays * DAY_MILLIS

        commitmentConfigDao.update(draft.copy(isDraft = false))
        activeCommitmentDao.upsert(
            ActiveCommitmentEntity(
                configVersion = draft.version,
                activatedAtEpochMillis = trusted.nowEpochMillis,
                deadlineEpochMillis = deadline,
                status = CommitmentStatus.ACTIVE.name,
                authorizedRecovery = false,
            ),
        )
        securityEventLogger.log(SecurityEventType.ACTIVATION, outcome = "activated", configVersion = draft.version)
        Log.d(TAG, "Commitment activated: version=${draft.version}, allowance=${config.reelsAllowanceSeconds}s/${config.windowSeconds}s, locked until ${Date(deadline)}")
        return ActivationResult.Activated(draft.version, deadline)
    }

    /** Called by the recovery flow once a signed approval has been verified. Never called directly by the UI. */
    suspend fun applyAuthorizedRecovery(action: RecoveryAction): UpdateResult {
        val active = activeCommitmentDao.get() ?: return UpdateResult.NoActiveCommitment
        val newStatus = when (action) {
            RecoveryAction.UNLOCK_CONFIG_EDIT, RecoveryAction.RESET_COMMITMENT -> CommitmentStatus.RECOVERED
            RecoveryAction.EXTEND_ALLOWANCE_TODAY -> {
                // Documented limitation: there is no "bonus seconds" ledger in
                // the usage engine yet. Recorded as a security event so the
                // approval is auditable, but it does not change enforcement.
                securityEventLogger.log(
                    SecurityEventType.RECOVERY_APPROVED,
                    outcome = "not_implemented",
                    configVersion = active.configVersion,
                    details = "EXTEND_ALLOWANCE_TODAY has no enforcement effect in this build",
                )
                return UpdateResult.Updated(active.configVersion)
            }
        }
        activeCommitmentDao.upsert(active.copy(status = newStatus.name, authorizedRecovery = true))
        securityEventLogger.log(SecurityEventType.RECOVERY_APPROVED, outcome = action.name, configVersion = active.configVersion)
        return UpdateResult.Updated(active.configVersion)
    }

    /** State diagram: Recovered/Expired -> Setup. Explicit user action after recovery/expiry, never automatic. */
    suspend fun beginReconfiguration(): UpdateResult {
        val active = activeCommitmentDao.get() ?: return UpdateResult.NoActiveCommitment
        val status = CommitmentStatus.valueOf(active.status)
        if (status != CommitmentStatus.RECOVERED && status != CommitmentStatus.EXPIRED) {
            return UpdateResult.Locked
        }
        activeCommitmentDao.upsert(active.copy(status = CommitmentStatus.SETUP.name, authorizedRecovery = false))
        return UpdateResult.Updated(active.configVersion)
    }

    private suspend fun reconcileExpiry() {
        val active = activeCommitmentDao.get() ?: return
        if (CommitmentStatus.valueOf(active.status) != CommitmentStatus.ACTIVE) return
        val now = trustedTimeManager.now().nowEpochMillis
        if (now >= active.deadlineEpochMillis) {
            activeCommitmentDao.upsert(active.copy(status = CommitmentStatus.EXPIRED.name))
            securityEventLogger.log(SecurityEventType.EXPIRY_REACHED, outcome = "expired", configVersion = active.configVersion)
            Log.d(TAG, "Commitment expired: version=${active.configVersion}")
        }
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val TAG = "ReelLock"
    }
}

private fun CommitmentConfigEntity.toConfig() = CommitmentConfig(
    version = version,
    reelsAllowanceSeconds = reelsAllowanceSeconds,
    windowSeconds = windowSeconds,
    commitmentDays = commitmentDays,
    recoveryMode = RecoveryMode.valueOf(recoveryMode),
    enforcementMode = EnforcementMode.valueOf(enforcementMode),
    targetPackage = targetPackage,
    blockUnknownScreens = blockUnknownScreens,
    allowRecoveryDuringLock = allowRecoveryDuringLock,
)

private fun CommitmentConfig.toEntity(version: Int, isDraft: Boolean, trustedPersonPublicKeyBase64: String? = null) = CommitmentConfigEntity(
    version = version,
    reelsAllowanceSeconds = reelsAllowanceSeconds,
    windowSeconds = windowSeconds,
    commitmentDays = commitmentDays,
    recoveryMode = recoveryMode.name,
    enforcementMode = enforcementMode.name,
    targetPackage = targetPackage,
    blockUnknownScreens = blockUnknownScreens,
    allowRecoveryDuringLock = allowRecoveryDuringLock,
    trustedPersonPublicKeyBase64 = trustedPersonPublicKeyBase64,
    createdAtEpochMillis = System.currentTimeMillis(),
    isDraft = isDraft,
)
