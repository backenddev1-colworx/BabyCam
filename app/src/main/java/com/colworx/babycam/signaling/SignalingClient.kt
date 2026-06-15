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

        Thread {
            try {
                val clientId = "babycam-$selfId-${System.currentTimeMillis()}"
                Log.d(TAG, "Connecting to $brokerUrl, topic=$topic, clientId=$clientId")
                val mqtt = MqttClient(brokerUrl, clientId, MemoryPersistence())

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
            } catch (e: Exception) {
                Log.e(TAG, "MQTT connect failed: ${e.javaClass.simpleName}: ${e.message}")
                onState(false)
            }
        }.start()
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
