package com.colworx.babycam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.colworx.babycam.ui.components.AppCard
import com.colworx.babycam.ui.components.PrimaryButton
import com.colworx.babycam.ui.components.SecondaryButton
import com.colworx.babycam.ui.components.StatusPill
import com.colworx.babycam.webrtc.LiveSession
import com.colworx.babycam.webrtc.VideoRenderer
import com.colworx.babycam.ui.theme.AlertRed
import com.colworx.babycam.ui.theme.BabyCamTheme
import com.colworx.babycam.ui.theme.IndigoDeep
import com.colworx.babycam.ui.theme.Indigo900
import com.colworx.babycam.ui.theme.Lavender50
import com.colworx.babycam.ui.theme.Muted
import com.colworx.babycam.ui.theme.NightSurface
import com.colworx.babycam.ui.theme.NightText
import com.colworx.babycam.ui.theme.Teal
import com.colworx.babycam.ui.theme.TealBg

@Composable
fun BabyPairingScreen(
    room: String,
    onContinue: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val signalingUp by LiveSession.signalingUp
    val statusText = if (signalingUp) "Waiting for parent…" else "Connecting…"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Lavender50)
            .safeDrawingPadding()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Pair with parent",
            style = MaterialTheme.typography.titleLarge,
            color = Indigo900,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Enter this code on the parent phone",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        // 4-digit code display
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            room.forEach { digit ->
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = digit.toString(),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Indigo900
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        StatusPill(
            text = statusText,
            bg = Color(0xFFFAEEDA),
            fg = Color(0xFF854F0B)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Keep this phone near baby",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(
            text = "Go to monitor",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BabyActiveScreen(onStop: () -> Unit, onSettings: () -> Unit = {}) {
    val camOn by LiveSession.babyCamEnabled
    val micOn by LiveSession.babyMicEnabled
    val cryOn by LiveSession.babyCryDetectionEnabled
    val lullabyOn by LiveSession.babyLullabyPlaying
    val torchOn by LiveSession.babyTorchOn
    val parentSharing by LiveSession.parentCamSharing
    val anythingActive = camOn || micOn || cryOn || torchOn || lullabyOn || parentSharing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Lavender50)
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                text = if (anythingActive) "Parent controls active" else "Standby · all controls off",
                bg = if (anythingActive) TealBg else Color(0xFFFAEEDA),
                fg = if (anythingActive) Teal else Color(0xFF854F0B)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = IndigoDeep
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NightSurface)
        ) {
            val connection = LiveSession.connection
            val localTrack by LiveSession.localVideo
            if (connection != null && camOn) {
                VideoRenderer(
                    track = localTrack,
                    eglContext = connection.eglContext,
                    modifier = Modifier.fillMaxSize(),
                    mirror = false
                )
            }
            if (!camOn) {
                // The parent turned the camera off to save battery/heat (capturer is actually
                // stopped, not just hidden — see WebRtcSession.setCameraStandby).
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VideocamOff,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Power-save — camera off",
                        style = MaterialTheme.typography.labelSmall,
                        color = NightText
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (camOn) {
                    Icon(
                        imageVector = Icons.Outlined.FiberManualRecord,
                        contentDescription = null,
                        tint = AlertRed,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = NightText
                    )
                }
            }

            // Picture-in-picture of the parent's camera — only while the parent is actively
            // sharing it (on-demand two-way video). Hidden otherwise so there's no black box.
            val parentTrack by LiveSession.remoteVideo
            if (parentSharing && parentTrack != null && connection != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(width = 92.dp, height = 124.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black)
                ) {
                    VideoRenderer(
                        track = parentTrack,
                        eglContext = connection.eglContext,
                        modifier = Modifier.fillMaxSize(),
                        mirror = false
                    )
                    Text(
                        text = "Parent",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Paired status — the pairing code (so a new parent can still pair) + whether a parent is
        // currently connected. This replaces re-showing the setup screen for an already-paired baby.
        val connStateBaby by LiveSession.connState
        val parentConnected = connStateBaby == org.webrtc.PeerConnection.IceConnectionState.CONNECTED ||
            connStateBaby == org.webrtc.PeerConnection.IceConnectionState.COMPLETED
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pairing code",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted
                    )
                    Text(
                        text = LiveSession.room.ifEmpty { "----" },
                        style = MaterialTheme.typography.titleLarge,
                        color = Indigo900,
                        fontWeight = FontWeight.Bold
                    )
                }
                StatusPill(
                    text = if (parentConnected) "Parent connected" else "Waiting for parent…",
                    bg = if (parentConnected) TealBg else Color(0xFFFAEEDA),
                    fg = if (parentConnected) Teal else Color(0xFF854F0B)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PhotoCamera,
                caption = if (camOn) "Camera ON" else "Camera OFF",
                active = camOn,
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Mic,
                caption = if (micOn) "Mic ON" else "Mic OFF",
                active = micOn,
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Hearing,
                caption = if (cryOn) "Cry ON" else "Cry OFF",
                active = cryOn,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.DevicesOther,
                    contentDescription = null,
                    tint = IndigoDeep,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = "Parent controls start OFF · reboot requires tap to resume",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IndigoDeep
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SecondaryButton(
            text = "Stop monitoring",
            onClick = onStop,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FeatureCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    caption: String,
    active: Boolean = false,
) {
    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = caption,
                tint = if (active) Teal else Muted,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) Teal else Muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BabyPairingScreenPreview() {
    BabyCamTheme {
        BabyPairingScreen(room = "DEMO")
    }
}

@Preview(showBackground = true)
@Composable
private fun BabyActiveScreenPreview() {
    BabyCamTheme {
        BabyActiveScreen(onStop = {})
    }
}
