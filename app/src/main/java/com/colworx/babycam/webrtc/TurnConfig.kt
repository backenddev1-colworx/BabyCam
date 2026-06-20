package com.colworx.babycam.webrtc

import org.webrtc.PeerConnection

/**
 * Central, provider-agnostic STUN/TURN configuration.
 *
 * Switching the TURN relay provider is a ONE-LINE change: set [ACTIVE_PROVIDER]. Every provider's
 * endpoints and credentials live in one place below, so we can move between Metered, Cloudflare, a
 * self-hosted coturn (Oracle / Hetzner / any VM), or STUN-only — without touching WebRtcSession.
 *
 * Free STUN (Google) is ALWAYS included regardless of the chosen provider; it covers same-network
 * and most home-NAT connections for free. TURN only kicks in when a direct/STUN path fails.
 *
 * Credential models:
 *  - Static-credential providers (Metered, self-hosted coturn) carry a long-lived username/password
 *    and work immediately from the values below.
 *  - Cloudflare Realtime TURN uses SHORT-LIVED credentials fetched per session from its API, so they
 *    cannot be hardcoded — fill in the key id/token, fetch creds at session start, then build the
 *    server list via [Cloudflare.servers]. See the runtime hook note in [iceServers].
 */
object TurnConfig {

    /** TURN relay services we know how to build an ICE-server list for. */
    enum class Provider {
        /** STUN only, no relay. Same-network / open-NAT only. Free, zero infra. */
        STUN_ONLY,

        /** Metered.ca relay — static long-term credentials (Free Trial 500 MB / Free 20 GB / paid). */
        METERED,

        /** Self-hosted coturn on Oracle / Hetzner / any VM — static credentials. */
        SELF_HOSTED_COTURN,

        /** Cloudflare Realtime TURN (1 TB/mo free) — ephemeral credentials fetched from the API. */
        CLOUDFLARE,
    }

    /**
     * ⬅️ CHANGE THIS LINE to switch the live TURN provider for the whole app.
     * Default keeps the existing Metered behaviour.
     */
    val ACTIVE_PROVIDER = Provider.CLOUDFLARE

    // ---------------------------------------------------------------------------------------------
    // Always-on free STUN (same-network + most home NATs). Never costs anything.
    // ---------------------------------------------------------------------------------------------
    private val stunServers: List<PeerConnection.IceServer> = listOf(
        ice("stun:stun.l.google.com:19302"),
        ice("stun:stun.relay.metered.ca:80"),
    )

    // ---------------------------------------------------------------------------------------------
    // Metered (static credentials). These are the creds currently shipped in the app.
    // ---------------------------------------------------------------------------------------------
    object Metered {
        const val host = "global.relay.metered.ca"
        const val username = "b802535bd8fa917ceca159d0"
        const val credential = "MaYcJAzJawOa/L5Q"

        fun servers(): List<PeerConnection.IceServer> = listOf(
            ice("turn:$host:80", username, credential),
            ice("turn:$host:80?transport=tcp", username, credential),
            ice("turn:$host:443", username, credential),
            ice("turns:$host:443?transport=tcp", username, credential),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Self-hosted coturn (Oracle / Hetzner). Fill these in once the VM is up (docs/turn-server-setup.md).
    // ---------------------------------------------------------------------------------------------
    object SelfHostedCoturn {
        const val host = "YOUR_SERVER_IP_OR_DOMAIN"   // e.g. 203.0.113.10 or turn.yourdomain.com
        const val username = "babycam"
        const val credential = "CHANGE_ME"

        fun servers(): List<PeerConnection.IceServer> = listOf(
            ice("turn:$host:3478", username, credential),
            ice("turn:$host:3478?transport=tcp", username, credential),
            // Enable once TLS (Let's Encrypt) is configured on the server:
            // ice("turns:$host:443?transport=tcp", username, credential),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Cloudflare Realtime TURN (1 TB/mo free). Credentials are short-lived and must be fetched from
    // the Cloudflare API at session start using the key id/token, then passed into servers().
    // ---------------------------------------------------------------------------------------------
    object Cloudflare {
        const val keyId = "5a5c4d48eb458f7464af9273729c9e79"
        const val apiToken = "cafc49f1b1dd133dc0c50488c88f5aab55f517bf274b34e0da606716218ccacb"

        /** Build the ICE list from credentials returned by the Cloudflare API at runtime. */
        fun servers(username: String, credential: String): List<PeerConnection.IceServer> = listOf(
            ice("turn:turn.cloudflare.com:3478?transport=udp", username, credential),
            ice("turn:turn.cloudflare.com:3478?transport=tcp", username, credential),
            ice("turns:turn.cloudflare.com:5349?transport=tcp", username, credential),
        )
    }

    /**
     * ICE server list for [ACTIVE_PROVIDER]. STUN is always prepended.
     *
     * NOTE on Cloudflare: its credentials are ephemeral, so this returns STUN only for that provider.
     * At session start, fetch creds from the Cloudflare API (using [Cloudflare.keyId]/[Cloudflare.apiToken])
     * and append [Cloudflare.servers] to this list before creating the PeerConnection.
     */
    fun iceServers(): List<PeerConnection.IceServer> = stunServers + when (ACTIVE_PROVIDER) {
        Provider.STUN_ONLY -> emptyList()
        Provider.METERED -> Metered.servers()
        Provider.SELF_HOSTED_COTURN -> SelfHostedCoturn.servers()
        Provider.CLOUDFLARE -> emptyList() // appended at runtime after the API credential fetch
    }

    private fun ice(url: String): PeerConnection.IceServer =
        PeerConnection.IceServer.builder(url).createIceServer()

    private fun ice(url: String, user: String, cred: String): PeerConnection.IceServer =
        PeerConnection.IceServer.builder(url).setUsername(user).setPassword(cred).createIceServer()
}
