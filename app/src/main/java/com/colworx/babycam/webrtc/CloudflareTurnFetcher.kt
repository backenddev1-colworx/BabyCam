package com.colworx.babycam.webrtc

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.webrtc.PeerConnection
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches short-lived TURN credentials from the Cloudflare Realtime API.
 *
 * Cloudflare TURN does not accept static credentials — each session requires ephemeral
 * username/credential pair fetched via a POST to:
 *   POST https://rtc.live.cloudflare.com/v1/turn/keys/{keyId}/credentials/generate
 *
 * The returned creds are valid for [ttlSeconds] (default 24 h). On any failure (network error,
 * non-200 response) the call returns null so the caller can fall back to Metered.
 */
object CloudflareTurnFetcher {

    private const val TAG = "BabyCam"
    private const val API = "https://rtc.live.cloudflare.com/v1/turn/keys"

    suspend fun fetch(
        keyId: String = TurnConfig.Cloudflare.keyId,
        apiToken: String = TurnConfig.Cloudflare.apiToken,
        ttlSeconds: Int = 86400,
    ): List<PeerConnection.IceServer>? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("$API/$keyId/credentials/generate")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write("""{"ttl":$ttlSeconds}""".toByteArray()) }

            val code = conn.responseCode
            if (code !in 200..299) {
                Log.e(TAG, "Cloudflare TURN creds fetch failed: HTTP $code")
                return@runCatching null
            }

            val body = conn.inputStream.bufferedReader().readText()
            val ice = JSONObject(body).getJSONObject("iceServers")
            val username = ice.getString("username")
            val credential = ice.getString("credential")
            Log.d(TAG, "Cloudflare TURN creds fetched ok (ttl=${ttlSeconds}s)")
            TurnConfig.Cloudflare.servers(username, credential)
        }.getOrElse {
            Log.e(TAG, "Cloudflare TURN creds fetch error", it)
            null
        }
    }
}
