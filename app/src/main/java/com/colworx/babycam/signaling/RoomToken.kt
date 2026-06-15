package com.colworx.babycam.signaling

import java.security.SecureRandom

/**
 * Generates and validates high-entropy pairing room tokens.
 *
 * Tokens are encoded with Crockford base32 (alphabet [A-Z2-7], no padding,
 * no ambiguous characters in this strict subset) so they survive being
 * embedded in a QR code and read back by the paired device.
 */
object RoomToken {

    /** Crockford-style base32 alphabet (upper-case, digits 2-7, no 0/1/8/9). */
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Number of characters emitted by [generate]. 26 chars of a 32-symbol alphabet ≈ 130 bits. */
    private const val TOKEN_LENGTH = 26

    private val random = SecureRandom()

    /**
     * Returns a fresh, cryptographically random pairing token of [TOKEN_LENGTH]
     * characters drawn from the Crockford base32 [ALPHABET].
     */
    fun generate(): String {
        val sb = StringBuilder(TOKEN_LENGTH)
        for (i in 0 until TOKEN_LENGTH) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)])
        }
        return sb.toString()
    }

    /**
     * Returns true when [token] is non-null, has a length within 20..40, and
     * only contains characters from the allowed [ALPHABET].
     */
    fun isValid(token: String?): Boolean {
        if (token == null) return false
        if (token.length !in 20..40) return false
        return token.all { it in ALPHABET }
    }
}
