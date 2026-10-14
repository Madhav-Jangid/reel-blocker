package com.reellock.reellock.recovery

import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * need.md section 7: ECDSA (P-256 / secp256r1) challenge-response. The
 * trusted person's keypair is generated OUTSIDE this app, on their own
 * device; ReelLock only ever handles their public key and their signatures.
 * See /tools/trusted_signer.py for a reference implementation of the
 * signer side (what a trusted person's "separate device" does).
 */
object RecoveryCrypto {

    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    fun generateNonce(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Canonical bytes that the trusted person must sign exactly - order and separators matter. */
    fun canonicalChallenge(
        nonce: String,
        deviceId: String,
        commitmentVersion: Int,
        requestedAction: String,
        expiresAtEpochMillis: Long,
    ): ByteArray {
        val s = listOf(nonce, deviceId, commitmentVersion.toString(), requestedAction, expiresAtEpochMillis.toString())
            .joinToString("|")
        return s.toByteArray(Charsets.UTF_8)
    }

    fun parsePublicKeyBase64(base64: String): PublicKey? = try {
        val bytes = Base64.getDecoder().decode(base64.trim())
        val keyFactory = KeyFactory.getInstance("EC")
        keyFactory.generatePublic(X509EncodedKeySpec(bytes))
    } catch (e: Exception) {
        null
    }

    fun verify(publicKey: PublicKey, challenge: ByteArray, signatureBase64: String): Boolean = try {
        val signatureBytes = Base64.getDecoder().decode(signatureBase64.trim())
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initVerify(publicKey)
        signature.update(challenge)
        signature.verify(signatureBytes)
    } catch (e: Exception) {
        false
    }
}
