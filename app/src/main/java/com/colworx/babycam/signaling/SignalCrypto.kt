package com.colworx.babycam.signaling

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Symmetric payload encryption for MQTT signaling.
 *
 * Both paired devices derive the same AES-256 key from the shared [RoomToken],
 * so signaling payloads (SDP / ICE) travelling through a public MQTT broker are
 * unreadable to the broker and anyone else who doesn't know the room token.
 *
 * Scheme: AES/GCM/NoPadding with a random 12-byte IV per message and a 128-bit
 * authentication tag. Wire format is `Base64(iv || ciphertext+tag)` (NO_WRAP).
 */
object SignalCrypto {

    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    /**
     * Derives a 32-byte AES key from the room token via SHA-256.
     *
     * @param roomToken the shared pairing token.
     * @return a 256-bit [SecretKeySpec] suitable for AES/GCM.
     */
    fun keyFromRoom(roomToken: String): SecretKeySpec {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(roomToken.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(hash, "AES")
    }

    /**
     * Encrypts [plaintext] with [key] using AES/GCM and a fresh random IV.
     *
     * @return `Base64(iv || ciphertext)` encoded with [Base64.NO_WRAP].
     */
    fun encrypt(key: SecretKeySpec, plaintext: String): String {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Reverses [encrypt]. Returns the empty string on any failure
     * (malformed input, wrong key, tampered ciphertext).
     */
    fun decrypt(key: SecretKeySpec, b64: String): String {
        return try {
            val combined = Base64.decode(b64, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_BYTES)
            val ciphertext = combined.copyOfRange(IV_BYTES, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
