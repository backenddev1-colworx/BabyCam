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
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.colworx.babycam.signaling.QrCodes
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
    val qrBitmap = remember(room) { QrCodes.encodeToBitmap(room, 600) }
    val signalingUp by LiveSession.signalingUp
    val statusText = if (signalingUp) "Waiting for parent…" else "Connecting…"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Lavender50)
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
            text = "Scan this on the parent phone",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(170.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "Pairing QR code",
                modifier = Modifier.size(150.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
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
        Spacer(modifier = Modifier.height(20.dp))
        PrimaryButton(
            text = "Go to monitor",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun BabyActiveScreen(onStop: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Lavender50)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StatusPill(
            text = "Monitoring active",
            bg = TealBg,
            fg = Teal
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NightSurface)
        ) {
            val connection = LiveSession.connection
            if (connection != null) {
                VideoRenderer(
                    track = connection.localVideoTrack,
                    eglContext = connection.eglContext,
                    modifier = Modifier.fillMaxSize(),
                    mirror = false
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PhotoCamera,
                caption = "Camera"
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Mic,
                caption = "Mic"
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Hearing,
                caption = "Cry"
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
                    text = "Runs in background & screen-off · auto-start on boot",
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
    caption: String
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
                tint = IndigoDeep,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
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
