package com.colworx.babycam.signaling

import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight one-off check of whether a baby device is currently online, without holding a
 * full WebRTC session open. Presence messages are correlated and never trigger negotiation.
 *
 * Used by the parent's multi-baby list to show live status without keeping a connection (and
 * battery drain) for every saved baby at once — only the room actually being viewed gets a
 * full [com.colworx.babycam.webrtc.BabyCamConnection].
 */
object PresenceChecker {

    fun check(room: String, timeoutMs: Long = 4000L, onResult: (Boolean) -> Unit) {
        val selfId = UUID.randomUUID().toString().take(8)
        val client = SignalingClient(selfId)
        val correlationId = UUID.randomUUID().toString()
        val resolved = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        var responseTimeout: Runnable? = null
        var connectTimeout: Runnable? = null

        fun finish(online: Boolean) {
            if (resolved.compareAndSet(false, true)) {
                responseTimeout?.let(mainHandler::removeCallbacks)
                connectTimeout?.let(mainHandler::removeCallbacks)
                mainHandler.post { onResult(online) }
                client.close()
            }
        }
        responseTimeout = Runnable { finish(false) }
        connectTimeout = Runnable { finish(false) }

        client.connect(
            room = room,
            onMessage = { message ->
                if (message.type == "presence_pong" && message.payload == correlationId) finish(true)
            },
            onReady = {
                connectTimeout?.let(mainHandler::removeCallbacks)
                client.send("presence_ping", correlationId)
                responseTimeout?.let { mainHandler.postDelayed(it, timeoutMs) }
            },
            onState = {},
        )

        connectTimeout?.let { mainHandler.postDelayed(it, CONNECT_TIMEOUT_MS) }
    }

    private const val CONNECT_TIMEOUT_MS = 20_000L
}
