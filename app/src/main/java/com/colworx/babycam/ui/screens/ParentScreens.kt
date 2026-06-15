package com.colworx.babycam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.colworx.babycam.signaling.RoomToken
import com.colworx.babycam.ui.components.AppCard
import com.colworx.babycam.ui.components.PrimaryButton
import com.colworx.babycam.ui.components.RoundControl
import com.colworx.babycam.ui.components.StatusPill
import com.colworx.babycam.ui.theme.BabyCamTheme
import com.colworx.babycam.ui.theme.IndigoDeep
import com.colworx.babycam.ui.theme.Indigo900
import com.colworx.babycam.ui.theme.Lavender50
import com.colworx.babycam.ui.theme.Lavender100
import com.colworx.babycam.ui.theme.Muted
import com.colworx.babycam.ui.theme.NightBg
import com.colworx.babycam.ui.theme.NightSurface
import com.colworx.babycam.ui.theme.NightText
import com.colworx.babycam.webrtc.LiveSession
import com.colworx.babycam.webrtc.VideoRenderer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

// 1) Parent scan screen ------------------------------------------------------

@Composable
fun ParentScanScreen(onScanned: (String) -> Unit = {}, onManual: () -> Unit = {}) {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val token = result.contents
        if (token != null && RoomToken.isValid(token)) onScanned(token)
    }
    val options = ScanOptions().apply {
        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        setPrompt("Scan the baby phone's code")
        setBeepEnabled(false)
        setOrientationLocked(false)
    }
    LaunchedEffect(Unit) { launcher.launch(options) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Scan baby's code",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Point at the baby phone",
            style = MaterialTheme.typography.bodyMedium,
            color = NightText
        )
        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(240.dp)
                .background(NightSurface, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(150.dp),
                contentAlignment = Alignment.Center
            ) {
                // 4 corner brackets
                CornerBracket(Modifier.align(Alignment.TopStart), top = true, start = true)
                CornerBracket(Modifier.align(Alignment.TopEnd), top = true, start = false)
                CornerBracket(Modifier.align(Alignment.BottomStart), top = false, start = true)
                CornerBracket(Modifier.align(Alignment.BottomEnd), top = false, start = false)
                Icon(
                    imageVector = Icons.Outlined.QrCode2,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = Color(0xFF3A3658)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Auto-detects the code",
            style = MaterialTheme.typography.labelSmall,
            color = NightText
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = "Open scanner", onClick = { launcher.launch(options) })
    }
}

@Composable
private fun CornerBracket(modifier: Modifier, top: Boolean, start: Boolean) {
    Box(
        modifier = modifier
            .size(28.dp)
            .border(
                width = 3.dp,
                color = com.colworx.babycam.ui.theme.Indigo,
                shape = RoundedCornerShape(
                    topStart = if (top && start) 8.dp else 0.dp,
                    topEnd = if (top && !start) 8.dp else 0.dp,
                    bottomStart = if (!top && start) 8.dp else 0.dp,
                    bottomEnd = if (!top && !start) 8.dp else 0.dp
                )
            )
    )
}

// 2) Parent live screen ------------------------------------------------------

@Composable
fun ParentLiveScreen(
    onNight: () -> Unit = {},
    onTalk: () -> Unit = {},
    onLullaby: () -> Unit = {},
    onSnapshot: () -> Unit = {}
) {
    val track by LiveSession.remoteVideo
    val connection = LiveSession.connection
    var talking by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBg)
    ) {
        if (connection != null) {
            VideoRenderer(
                track = track,
                eglContext = connection.eglContext,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Placeholder video area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NightSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChildCare,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFF2C2850)
                )
            }
        }

        // Top-left overlay
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusPill(
                text = "LIVE",
                bg = Color(0x2EE24B4A),
                fg = Color(0xFFFF8B8A)
            )
            Icon(
                imageVector = Icons.Outlined.BatteryStd,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = NightText
            )
            Text(
                text = "Baby 78%",
                style = MaterialTheme.typography.labelSmall,
                color = NightText
            )
        }

        // Bottom control bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(vertical = 16.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RoundControl(icon = Icons.Outlined.DarkMode, label = "Night", onClick = onNight)
            RoundControl(
                icon = Icons.Outlined.Mic,
                label = if (talking) "Talking" else "Talk",
                onClick = {
                    talking = !talking
                    LiveSession.setTalking(talking)
                    onTalk()
                },
                size = 54,
                bg = IndigoDeep
            )
            RoundControl(icon = Icons.Outlined.MusicNote, label = "Lullaby", onClick = onLullaby)
            RoundControl(icon = Icons.Outlined.PhotoCamera, label = "Snap", onClick = onSnapshot)
        }
    }
}

// 3) Lullaby picker sheet ----------------------------------------------------

@Composable
fun LullabyPickerSheet(onSelect: (String) -> Unit = {}, onStop: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Lavender50, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .background(Color(0xFFBDBDBD), RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Play a sound",
                style = MaterialTheme.typography.titleMedium,
                color = Indigo900
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Plays on the baby unit",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
            Spacer(Modifier.height(16.dp))

            SoundRow("Soft lullaby", Icons.Outlined.MusicNote, selected = true) { onSelect("Soft lullaby") }
            Spacer(Modifier.height(10.dp))
            SoundRow("White noise", Icons.Outlined.Air, selected = false) { onSelect("White noise") }
            Spacer(Modifier.height(10.dp))
            SoundRow("Heartbeat", Icons.Outlined.Favorite, selected = false) { onSelect("Heartbeat") }
            Spacer(Modifier.height(10.dp))
            SoundRow("Rain", Icons.Outlined.Cloud, selected = false) { onSelect("Rain") }
        }
    }
}

@Composable
private fun SoundRow(
    name: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val rowModifier = if (selected) {
        Modifier
            .fillMaxWidth()
            .background(IndigoDeep, shape)
    } else {
        Modifier
            .fillMaxWidth()
            .background(Color.White, shape)
            .border(1.dp, Lavender100, shape)
    }
    Row(
        modifier = rowModifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else IndigoDeep
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Color.White else Indigo900
        )
    }
}

// 4) Settings screen ---------------------------------------------------------

@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    var crySensitivity by remember { mutableFloatStateOf(0.55f) }
    var autoStart by remember { mutableStateOf(true) }
    var dataSaver by remember { mutableStateOf(false) }
    var appLock by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Lavender50)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            color = Indigo900
        )
        Spacer(Modifier.height(16.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Cry sensitivity",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Indigo900
                )
                Slider(
                    value = crySensitivity,
                    onValueChange = { crySensitivity = it }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SwitchRow("Auto-start on boot", autoStart) { autoStart = it }
                Spacer(Modifier.height(8.dp))
                SwitchRow("Data-saver (audio only)", dataSaver) { dataSaver = it }
                Spacer(Modifier.height(8.dp))
                SwitchRow("App lock (PIN/biometric)", appLock) { appLock = it }
            }
        }
        Spacer(Modifier.height(12.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "About BabyCam",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Indigo900,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = IndigoDeep
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Indigo900,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// Previews -------------------------------------------------------------------

@Preview
@Composable
private fun ParentScanScreenPreview() {
    BabyCamTheme { ParentScanScreen() }
}

@Preview
@Composable
private fun ParentLiveScreenPreview() {
    BabyCamTheme { ParentLiveScreen() }
}

@Preview
@Composable
private fun LullabyPickerSheetPreview() {
    BabyCamTheme { LullabyPickerSheet() }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    BabyCamTheme { SettingsScreen() }
}
