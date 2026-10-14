package com.reellock.reellock.recovery

import com.reellock.reellock.commitment.CommitmentEngine
import com.reellock.reellock.commitment.RecoveryAction
import com.reellock.reellock.commitment.RecoveryRequestStatus
import com.reellock.reellock.commitment.SecurityEventType
import com.reellock.reellock.persistence.ActiveCommitmentDao
import com.reellock.reellock.persistence.CommitmentConfigDao
import com.reellock.reellock.persistence.RecoveryApprovalDao
import com.reellock.reellock.persistence.RecoveryApprovalEntity
import com.reellock.reellock.persistence.RecoveryRequestDao
import com.reellock.reellock.persistence.RecoveryRequestEntity
import com.reellock.reellock.security.SecurityEventLogger
import com.reellock.reellock.time.TrustedTimeManager

sealed class RecoveryRequestResult {
    data class Created(val nonce: String, val challenge: String, val expiresAtEpochMillis: Long) : RecoveryRequestResult()
    object NoActiveCommitment : RecoveryRequestResult()
    object NoTrustedPersonKeyConfigured : RecoveryRequestResult()
}

sealed class RecoveryApprovalResult {
    data class Approved(val action: RecoveryAction) : RecoveryApprovalResult()
    object NotFound : RecoveryApprovalResult()
    object Expired : RecoveryApprovalResult()
    object AlreadyResolved : RecoveryApprovalResult()
    object InvalidSignature : RecoveryApprovalResult()
    object NoTrustedPersonKeyConfigured : RecoveryApprovalResult()
}

/**
 * need.md section 7. The trusted person's public key is pinned once (during
 * setup) into the active CommitmentConfig row; submitApproval() only ever
 * verifies against that pinned key - a request can never carry its own
 * "trust me, here's the key" payload, which would defeat the whole point.
 */
class RecoveryManager(
    private val recoveryRequestDao: RecoveryRequestDao,
    private val recoveryApprovalDao: RecoveryApprovalDao,
    private val activeCommitmentDao: ActiveCommitmentDao,
    private val commitmentConfigDao: CommitmentConfigDao,
    private val commitmentEngine: CommitmentEngine,
    private val deviceIdentity: DeviceIdentity,
    private val trustedTimeManager: TrustedTimeManager,
    private val securityEventLogger: SecurityEventLogger,
) {

    suspend fun createRequest(reason: String, action: RecoveryAction, ttlMillis: Long = DEFAULT_TTL_MILLIS): RecoveryRequestResult {
        val active = activeCommitmentDao.get() ?: return RecoveryRequestResult.NoActiveCommitment
        val config = commitmentConfigDao.getByVersion(active.configVersion) ?: return RecoveryRequestResult.NoActiveCommitment
        if (config.trustedPersonPublicKeyBase64.isNullOrBlank()) {
            return RecoveryRequestResult.NoTrustedPersonKeyConfigured
        }

        val now = trustedTimeManager.now().nowEpochMillis
        val nonce = RecoveryCrypto.generateNonce()
        val deviceId = deviceIdentity.get()
        val expiresAt = now + ttlMillis

        recoveryRequestDao.insert(
            RecoveryRequestEntity(
                nonce = nonce,
                reason = reason,
                requestedAction = action.name,
                createdAtEpochMillis = now,
                expiresAtEpochMillis = expiresAt,
                commitmentVersion = active.configVersion,
                deviceId = deviceId,
                status = RecoveryRequestStatus.PENDING.name,
            ),
        )
        securityEventLogger.log(SecurityEventType.RECOVERY_REQUESTED, outcome = action.name, configVersion = active.configVersion, details = reason)

        val challenge = RecoveryCrypto.canonicalChallenge(nonce, deviceId, active.configVersion, action.name, expiresAt)
        return RecoveryRequestResult.Created(nonce, challenge.toString(Charsets.UTF_8), expiresAt)
    }

    /** `signatureBase64` is whatever the trusted person produced on their own device for the exact challenge string from [createRequest]. */
    suspend fun submitApproval(nonce: String, signatureBase64: String): RecoveryApprovalResult {
        val request = recoveryRequestDao.getByNonce(nonce) ?: return RecoveryApprovalResult.NotFound
        val now = trustedTimeManager.now().nowEpochMillis

        if (request.status != RecoveryRequestStatus.PENDING.name) {
            return RecoveryApprovalResult.AlreadyResolved
        }
        if (now > request.expiresAtEpochMillis) {
            recoveryRequestDao.update(request.copy(status = RecoveryRequestStatus.EXPIRED.name))
            return RecoveryApprovalResult.Expired
        }

        val config = commitmentConfigDao.getByVersion(request.commitmentVersion)
        val pinnedKeyBase64 = config?.trustedPersonPublicKeyBase64
        if (pinnedKeyBase64.isNullOrBlank()) {
            return RecoveryApprovalResult.NoTrustedPersonKeyConfigured
        }
        val publicKey = RecoveryCrypto.parsePublicKeyBase64(pinnedKeyBase64)
        if (publicKey == null) {
            return RecoveryApprovalResult.InvalidSignature
        }

        val challenge = RecoveryCrypto.canonicalChallenge(
            request.nonce,
            request.deviceId,
            request.commitmentVersion,
            request.requestedAction,
            request.expiresAtEpochMillis,
        )
        val verified = RecoveryCrypto.verify(publicKey, challenge, signatureBase64)
        if (!verified) {
            recoveryRequestDao.update(request.copy(status = RecoveryRequestStatus.DENIED.name))
            securityEventLogger.log(SecurityEventType.RECOVERY_REJECTED, outcome = "invalid_signature", configVersion = request.commitmentVersion)
            return RecoveryApprovalResult.InvalidSignature
        }

        recoveryRequestDao.update(request.copy(status = RecoveryRequestStatus.CONSUMED.name))
        recoveryApprovalDao.insert(
            RecoveryApprovalEntity(
                requestNonce = request.nonce,
                approverPublicKeyBase64 = pinnedKeyBase64,
                signatureBase64 = signatureBase64,
                approvedAtEpochMillis = now,
                used = true,
            ),
        )

        val action = RecoveryAction.valueOf(request.requestedAction)
        commitmentEngine.applyAuthorizedRecovery(action)
        return RecoveryApprovalResult.Approved(action)
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 24L * 60 * 60 * 1000 // 24h to exchange challenge/signature offline
    }
}
