package com.reellock.reellock.recovery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class RecoveryCryptoTest {

    private fun generateP256KeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun sign(privateKey: java.security.PrivateKey, data: ByteArray): String {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    @Test
    fun `valid signature over the exact challenge verifies`() {
        val keyPair = generateP256KeyPair()
        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val challenge = RecoveryCrypto.canonicalChallenge("nonce-1", "device-1", 1, "UNLOCK_CONFIG_EDIT", 999)
        val signatureBase64 = sign(keyPair.private, challenge)

        val parsedPublicKey = RecoveryCrypto.parsePublicKeyBase64(publicKeyBase64)
        assertNotNull(parsedPublicKey)
        assertTrue(RecoveryCrypto.verify(parsedPublicKey!!, challenge, signatureBase64))
    }

    @Test
    fun `signature over a different challenge is rejected`() {
        val keyPair = generateP256KeyPair()
        val challenge = RecoveryCrypto.canonicalChallenge("nonce-1", "device-1", 1, "UNLOCK_CONFIG_EDIT", 999)
        val tamperedChallenge = RecoveryCrypto.canonicalChallenge("nonce-1", "device-1", 1, "RESET_COMMITMENT", 999)
        val signatureBase64 = sign(keyPair.private, challenge)

        val publicKey = RecoveryCrypto.parsePublicKeyBase64(Base64.getEncoder().encodeToString(keyPair.public.encoded))!!
        assertFalse(RecoveryCrypto.verify(publicKey, tamperedChallenge, signatureBase64))
    }

    @Test
    fun `signature from a different key pair is rejected`() {
        val signerKeyPair = generateP256KeyPair()
        val attackerKeyPair = generateP256KeyPair()
        val challenge = RecoveryCrypto.canonicalChallenge("nonce-1", "device-1", 1, "UNLOCK_CONFIG_EDIT", 999)
        val signatureBase64 = sign(attackerKeyPair.private, challenge)

        val pinnedPublicKey = RecoveryCrypto.parsePublicKeyBase64(Base64.getEncoder().encodeToString(signerKeyPair.public.encoded))!!
        assertFalse(RecoveryCrypto.verify(pinnedPublicKey, challenge, signatureBase64))
    }

    @Test
    fun `garbage public key fails to parse instead of throwing`() {
        assertNull(RecoveryCrypto.parsePublicKeyBase64("not-a-valid-key"))
    }

    @Test
    fun `nonces are unique and url-safe`() {
        val nonces = (1..50).map { RecoveryCrypto.generateNonce() }
        assertTrue(nonces.toSet().size == nonces.size)
        assertTrue(nonces.all { !it.contains("+") && !it.contains("/") })
    }
}
