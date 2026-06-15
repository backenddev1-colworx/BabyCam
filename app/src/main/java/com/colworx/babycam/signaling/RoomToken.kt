package com.colworx.babycam.signaling

import java.security.SecureRandom

object RoomToken {

    private val random = SecureRandom()

    /** Returns a random 4-digit pairing code, zero-padded (e.g. "0472"). */
    fun generate(): String = String.format("%04d", random.nextInt(10000))

    /** A valid token is exactly 4 decimal digits. */
    fun isValid(token: String?): Boolean =
        token != null && token.length == 4 && token.all { it.isDigit() }
}
