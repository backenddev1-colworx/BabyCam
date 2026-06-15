package com.colworx.babycam.signaling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * A single signaling message exchanged between the two paired devices.
 *
 * @property type one of `offer`, `answer`, `ice`, `event`.
 * @property from the sender's [SignalingClient.selfId].
 * @property payload opaque string payload (e.g. an SDP blob or ICE candidate JSON).
 */
data class SignalMessage(val type: String, val from: String, val payload: String)

/**
 * WebSocket signaling client built on top of Supabase Realtime (Phoenix protocol).
 *
 * ## Protocol
 * - Transport: Phoenix channels over WebSocket at `/realtime/v1/websocket`.
 * - Topic: `realtime:babycam:<room>` where `<room>` is the pairing [RoomToken].
 * - On connect we `phx_join` the topic with `broadcast.self = false` so we don't
 *   receive our own messages.
 * - Application messages are sent as Realtime `broadcast` events carrying a
 *   [SignalMessage] JSON object under `payload.payload`.
 * - A `heartbeat` frame is sent to the `phoenix` topic every 30 seconds to keep
 *   the socket alive.
 *
 * ## Integration (for Kamran)
 * Construct with the live Supabase project URL and anon key:
 * ```
 * val client = SignalingClient(
 *     supabaseUrl = "https://<project-ref>.supabase.co",
 *     anonKey = "<supabase-anon-key>",
 *     selfId = "<device-id>"
 * )
 * ```
 * These are injected at runtime; this class keeps no hard-coded config.
 */
class SignalingClient(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val selfId: String
) {

    private val client = OkHttpClient()
    private var ws: WebSocket? = null
    private var room: String = ""

    private val refCounter = AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var heartbeatJob: Job? = null

    private fun nextRef(): String = refCounter.incrementAndGet().toString()

    private fun topic(): String = "realtime:babycam:$room"

    /**
     * Opens the WebSocket, joins the room channel and begins relaying messages.
     *
     * @param room the pairing room id (a [RoomToken]).
     * @param onMessage invoked on the OkHttp dispatcher thread for each inbound [SignalMessage].
     * @param onState invoked with `true` once the channel join is sent, `false` on close/failure.
     */
    fun connect(
        room: String,
        onMessage: (SignalMessage) -> Unit,
        onState: (Boolean) -> Unit
    ) {
        this.room = room

        val url = supabaseUrl.replace("https://", "wss://").trimEnd('/') +
            "/realtime/v1/websocket?apikey=" + anonKey + "&vsn=1.0.0"

        val request = Request.Builder().url(url).build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val join = JSONObject().apply {
                    put("topic", topic())
                    put("event", "phx_join")
                    put(
                        "payload",
                        JSONObject().put(
                            "config",
                            JSONObject().put(
                                "broadcast",
                                JSONObject().put("self", false)
                            )
                        )
                    )
                    put("ref", "1")
                }
                webSocket.send(join.toString())
                startHeartbeat()
                onState(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleInbound(text, onMessage)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                onState(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onState(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onState(false)
            }
        })
    }

    private fun handleInbound(text: String, onMessage: (SignalMessage) -> Unit) {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            return
        }

        val event = root.optString("event")
        // Ignore phx_reply, presence_state, presence_diff, heartbeat replies, etc.
        if (event != "broadcast") return

        // Phoenix broadcast frame: { event: "broadcast", payload: { ... our SignalMessage under payload.payload ... } }
        val outer = root.optJSONObject("payload") ?: return
        val inner = outer.optJSONObject("payload") ?: return

        val type = inner.optString("type")
        val from = inner.optString("from")
        val payload = inner.optString("payload")
        if (type.isEmpty()) return

        onMessage(SignalMessage(type = type, from = from, payload = payload))
    }

    /**
     * Sends a signaling message as a Realtime broadcast to the room.
     *
     * @param type one of `offer`, `answer`, `ice`, `event`.
     * @param payload opaque string payload.
     */
    fun send(type: String, payload: String) {
        val signal = JSONObject().apply {
            put("type", type)
            put("from", selfId)
            put("payload", payload)
        }

        val broadcastPayload = JSONObject().apply {
            put("event", "signal")
            put("type", "broadcast")
            put("payload", signal)
        }

        val frame = JSONObject().apply {
            put("topic", topic())
            put("event", "broadcast")
            put("payload", broadcastPayload)
            put("ref", nextRef())
        }

        ws?.send(frame.toString())
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(30_000L)
                val heartbeat = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", nextRef())
                }
                ws?.send(heartbeat.toString())
            }
        }
    }

    /** Stops the heartbeat and closes the WebSocket with a normal (1000) close code. */
    fun close() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        ws?.close(1000, null)
        ws = null
    }
}
