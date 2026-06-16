package com.colworx.babycam.signaling

import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight one-off check of whether a baby device is currently online, without holding a
 * full WebRTC session open. Connects to the room's MQTT topic, sends a "ping", and reports
 * online if the baby (which answers "ping" with a fresh "offer") replies within [timeoutMs].
 *
 * Used by the parent's multi-baby list to show live status without keeping a connection (and
 * battery drain) for every saved baby at once — only the room actually being viewed gets a
 * full [com.colworx.babycam.webrtc.BabyCamConnection].
 */
object PresenceChecker {

    fun check(room: String, timeoutMs: Long = 4000L, onResult: (Boolean) -> Unit) {
        val selfId = UUID.randomUUID().toString().take(8)
        val client = SignalingClient(selfId)
        val resolved = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())

        fun finish(online: Boolean) {
            if (resolved.compareAndSet(false, true)) {
                mainHandler.post { onResult(online) }
                client.close()
            }
        }

        client.connect(
            room = room,
            onMessage = { finish(true) },
            onState = { up -> if (up) client.send("ping", "") }
        )

        mainHandler.postDelayed({ finish(false) }, timeoutMs)
    }
}
