package com.colworx.babycam.signaling

import android.util.Log
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import javax.crypto.spec.SecretKeySpec

/**
 * A single signaling message exchanged between the two paired devices.
 *
 * @property type one of `offer`, `answer`, `ice`, `event`.
 * @property from the sender's [SignalingClient.selfId].
 * @property payload opaque string payload (e.g. an SDP blob or ICE candidate JSON).
 */
data class SignalMessage(val type: String, val from: String, val payload: String)

/**
 * Zero-account signaling client over a **public MQTT broker** (HiveMQ by default).
 *
 * ## Privacy
 * No Supabase account, no auth, no server config. Every device that knows the
 * pairing [RoomToken] subscribes to the same topic `babycam/<room>`. Because the
 * broker is public, all payloads are encrypted with a **per-room AES-256 key**
 * derived from the room token (see [SignalCrypto]). The broker therefore relays
 * only ciphertext and never sees the SDP/ICE contents.
 *
 * ## Protocol
 * - Topic: `babycam/<room>`.
 * - Each message is a JSON `{type, from, payload}` object, AES/GCM encrypted, then
 *   published as the MQTT payload (QoS 1).
 * - Devices ignore their own messages by comparing `from == selfId`.
 *
 * ## Integration
 * ```
 * val client = SignalingClient(selfId = "<device-id>")
 * client.connect(room, onMessage = { ... }, onState = { connected -> ... })
 * ```
 * The public-surface (constructor + [connect]/[send]/[close]) matches the previous
 * Supabase client so callers don't change beyond the constructor args.
 */
class SignalingClient(
    private val selfId: String,
    private val brokerUrl: String = "tcp://broker.hivemq.com:1883"
) {

    private var client: MqttClient? = null
    private lateinit var key: SecretKeySpec
    private var topic = ""

    /** Set by [close]; stops the initial-connection retry loop from reconnecting after teardown. */
    @Volatile private var closed = false

    /**
     * Connects to the broker, subscribes to the room topic and relays messages.
     *
     * Paho's [MqttClient.connect] blocks, so the connection is established on a
     * background thread; [onState] is invoked once connected (or `false` on error).
     *
     * @param room the pairing room id (a [RoomToken]).
     * @param onMessage invoked for each inbound (decrypted, non-self) [SignalMessage].
     * @param onState invoked with `true` once subscribed, `false` on failure/loss.
     */
    fun connect(
        room: String,
        onMessage: (SignalMessage) -> Unit,
        onReconnected: (() -> Unit)? = null,
        onState: (Boolean) -> Unit
    ) {
        topic = "babycam/$room"
        key = SignalCrypto.keyFromRoom(room)
        closed = false

        Thread {
            // Retry the *initial* connect with capped backoff. Paho's automaticReconnect only
            // kicks in after the first successful connect, so without this loop a single transient
            // failure (the free public broker frequently refuses connections) would leave the
            // device permanently offline — fatal for a baby monitor. Retries until connected or
            // close() is called.
            var attempt = 0
            while (!closed) {
                attempt++
                // Resolve to concrete IPv4 addresses and try each. On NAT64/DNS64 networks the
                // hostname resolves to a synthesized IPv6 address whose NAT64 gateway refuses the
                // connection (ECONNREFUSED), while the real IPv4 is reachable — Paho only tries
                // the first resolved address, so we must force IPv4 and iterate ourselves.
                val urls = resolveBrokerUrls()
                var ok = false
                for (url in urls) {
                    if (closed) break
                    ok = tryConnect(url, onMessage, onReconnected, onState)
                    if (ok) break
                }
                if (ok || closed) break
                val backoffMs = minOf(2000L * attempt, 15000L)
                Log.w(TAG, "All broker addresses failed (attempt $attempt); retrying in ${backoffMs}ms")
                try { Thread.sleep(backoffMs) } catch (_: InterruptedException) { break }
            }
        }.start()
    }

    /**
     * Resolves the broker hostname to candidate `tcp://ip:port` URLs, preferring IPv4. Returns the
     * original hostname URL as a fallback if resolution fails. IPv4 is preferred because some
     * networks use DNS64/NAT64 and hand back a synthesized IPv6 address that the NAT64 gateway
     * then refuses — the real IPv4 path works fine.
     */
    private fun resolveBrokerUrls(): List<String> = try {
        val uri = java.net.URI(brokerUrl)
        val host = uri.host
        val port = uri.port
        val all = java.net.InetAddress.getAllByName(host)
        val v4 = all.filterIsInstance<java.net.Inet4Address>()
        val chosen = if (v4.isNotEmpty()) v4 else all.toList()
        chosen.map { "tcp://${it.hostAddress}:$port" }
            .also { Log.d(TAG, "Resolved $host -> ${it.size} addr(s) (IPv4-preferred): $it") }
            .ifEmpty { listOf(brokerUrl) }
    } catch (e: Exception) {
        Log.w(TAG, "Broker DNS resolve failed (${e.message}); using hostname as-is")
        listOf(brokerUrl)
    }

    /**
     * One connection attempt to [serverUri]. Returns true if connected + subscribed, false on
     * failure so the caller's retry loop can try the next address / back off.
     */
    private fun tryConnect(
        serverUri: String,
        onMessage: (SignalMessage) -> Unit,
        onReconnected: (() -> Unit)?,
        onState: (Boolean) -> Unit
    ): Boolean {
            try {
                val clientId = "babycam-$selfId-${System.currentTimeMillis()}"
                Log.d(TAG, "Connecting to $serverUri, topic=$topic, clientId=$clientId")
                val mqtt = MqttClient(serverUri, clientId, MemoryPersistence())

                mqtt.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        if (reconnect) {
                            try {
                                mqtt.subscribe(topic, 1)
                                Log.d(TAG, "Reconnected and re-subscribed to $topic")
                                onState(true)
                                onReconnected?.invoke()
                            } catch (e: Exception) {
                                Log.e(TAG, "Re-subscribe after reconnect failed: ${e.message}")
                            }
                        }
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "Connection lost: ${cause?.message}")
                        onState(false)
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val raw = message?.payload ?: return
                        val plain = try {
                            SignalCrypto.decrypt(key, String(raw, Charsets.UTF_8))
                        } catch (e: Exception) {
                            Log.w(TAG, "Decrypt failed: ${e.message}")
                            ""
                        }
                        if (plain.isEmpty()) return

                        val json = try {
                            JSONObject(plain)
                        } catch (e: Exception) {
                            return
                        }

                        val from = json.optString("from")
                        if (from == selfId) return

                        val type = json.optString("type")
                        if (type.isEmpty()) return
                        val payload = json.optString("payload")
                        Log.d(TAG, "Received [$type] from $from")

                        onMessage(SignalMessage(type = type, from = from, payload = payload))
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    isAutomaticReconnect = true
                    keepAliveInterval = 30
                    connectionTimeout = 15
                }

                mqtt.connect(options)
                mqtt.subscribe(topic, 1)
                client = mqtt
                Log.d(TAG, "Connected and subscribed to $topic")
                onState(true)
                return true
            } catch (e: Exception) {
                // Paho wraps the real failure (DNS, refused, timeout, TLS) as a generic
                // "Unable to connect to server" — walk the cause chain to log the actual reason.
                val rootCause = generateSequence(e as Throwable) { it.cause }
                    .joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }
                Log.e(TAG, "MQTT connect failed: $rootCause")
                onState(false)
                return false
            }
    }

    /**
     * Encrypts and publishes a signaling message to the room topic (QoS 1).
     *
     * @param type one of `offer`, `answer`, `ice`, `event`.
     * @param payload opaque string payload.
     */
    fun send(type: String, payload: String, retained: Boolean = false) {
        try {
            val json = JSONObject().apply {
                put("type", type)
                put("from", selfId)
                put("payload", payload)
            }
            val encrypted = SignalCrypto.encrypt(key, json.toString())
            val message = MqttMessage(encrypted.toByteArray(Charsets.UTF_8)).apply {
                qos = 1
                isRetained = retained
            }
            client?.publish(topic, message)
            Log.d(TAG, "Sent [$type] retained=$retained")
        } catch (e: Exception) {
            Log.e(TAG, "Send [$type] failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BabyCam"
    }

    /** Disconnects and releases the MQTT client. */
    fun close() {
        closed = true
        try {
            client?.disconnect()
            client?.close()
        } catch (e: Exception) {
            // Ignore.
        } finally {
            client = null
        }
    }
}
