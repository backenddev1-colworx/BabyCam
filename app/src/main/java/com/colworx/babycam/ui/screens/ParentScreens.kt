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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.colworx.babycam.data.AppPreferences
import com.colworx.babycam.media.nightModeFilter
import com.colworx.babycam.security.PinManager
import com.colworx.babycam.service.MonitorController
import com.colworx.babycam.signaling.RoomToken
import com.colworx.babycam.ui.components.AppCard
import com.colworx.babycam.ui.components.PrimaryButton
import com.colworx.babycam.ui.components.RoundControl
import com.colworx.babycam.ui.components.SecondaryButton
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
import kotlinx.coroutines.launch

// 1) Parent scan screen ------------------------------------------------------

@Composable
fun ParentScanScreen(onScanned: (String) -> Unit = {}) {
    var code by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Auto-submit when 4 digits entered
    LaunchedEffect(code) {
        if (code.length == 4 && RoomToken.isValid(code)) onScanned(code)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Enter pairing code",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "4-digit code shown on baby phone",
            style = MaterialTheme.typography.bodyMedium,
            color = NightText
        )
        Spacer(Modifier.height(40.dp))

        // Hidden text field drives the OTP boxes
        BasicTextField(
            value = code,
            onValueChange = { v ->
                val digits = v.filter { it.isDigit() }.take(4)
                code = digits
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusRequester)
        )

        // 4 digit boxes
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i ->
                val digit = code.getOrNull(i)?.toString() ?: ""
                val filled = i < code.length
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (filled) IndigoDeep else NightSurface)
                        .border(
                            width = 2.dp,
                            color = if (filled) IndigoDeep else Color(0xFF2C2850),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { focusRequester.requestFocus() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = digit,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            text = "Tap a box if keyboard doesn't open",
            style = MaterialTheme.typography.labelSmall,
            color = NightText
        )
    }
}

// 2) Parent live screen ------------------------------------------------------

@Composable
fun ParentLiveScreen(
    onSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val track by LiveSession.remoteVideo
    val connState by LiveSession.connState
    val connection = LiveSession.connection
    val battery by LiveSession.babyBattery
    var talking by remember { mutableStateOf(false) }
    var nightMode by remember { mutableStateOf(false) }
    var showLullaby by remember { mutableStateOf(false) }

    val isDisconnected = connState == org.webrtc.PeerConnection.IceConnectionState.FAILED ||
        connState == org.webrtc.PeerConnection.IceConnectionState.DISCONNECTED ||
        connState == org.webrtc.PeerConnection.IceConnectionState.CLOSED

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBg)
    ) {
        if (connection != null) {
            VideoRenderer(
                track = track,
                eglContext = connection.eglContext,
                modifier = Modifier
                    .fillMaxSize()
                    .nightModeFilter(nightMode)
            )
        } else {
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

        // Lullaby picker sheet
        if (showLullaby) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .clickable { showLullaby = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                LullabyPickerSheet(
                    onSelect = { sound ->
                        val tag = when (sound) {
                            "Heartbeat" -> "heartbeat"
                            "Rain" -> "rain"
                            else -> "white_noise"
                        }
                        LiveSession.sendLullaby(tag)
                        showLullaby = false
                    },
                    onStop = {
                        LiveSession.sendLullaby("stop")
                        showLullaby = false
                    }
                )
            }
        }

        // Connection-lost overlay
        if (isDisconnected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.WifiOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Connection lost",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Reconnecting…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NightText
                    )
                }
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
                text = if (battery != null) "Baby ${battery}%" else "Baby --",
                style = MaterialTheme.typography.labelSmall,
                color = NightText
            )
        }

        // Top-right settings gear
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = NightText
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
            RoundControl(
                icon = Icons.Outlined.DarkMode,
                label = if (nightMode) "Night ON" else "Night",
                onClick = { nightMode = !nightMode },
                bg = if (nightMode) Color(0xFF1D3320) else Color(0x33FFFFFF),
                fg = if (nightMode) Color(0xFF4CAF50) else NightText
            )
            RoundControl(
                icon = Icons.Outlined.Mic,
                label = if (talking) "Talking" else "Talk",
                onClick = {
                    talking = !talking
                    LiveSession.setTalking(talking)
                },
                size = 54,
                bg = IndigoDeep
            )
            RoundControl(
                icon = Icons.Outlined.MusicNote,
                label = "Lullaby",
                onClick = { showLullaby = true }
            )
            RoundControl(
                icon = Icons.Outlined.PhotoCamera,
                label = "Snap",
                onClick = {
                    LiveSession.captureSnapshot(context) { ok ->
                        // Callback may run off the main thread — post the Toast on main.
                        ContextCompat.getMainExecutor(context).execute {
                            Toast.makeText(
                                context,
                                if (ok) "Saved to gallery" else "Snapshot failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
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
            Spacer(Modifier.height(16.dp))
            SecondaryButton(text = "Stop sound", onClick = onStop, modifier = Modifier.fillMaxWidth())
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
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val pinManager = remember { PinManager(context) }
    val scope = rememberCoroutineScope()

    var crySensitivity by remember { mutableFloatStateOf(0.55f) }
    var autoStart by remember { mutableStateOf(MonitorController.isAutoStartEnabled(context)) }
    var dataSaver by remember { mutableStateOf(false) }

    var showPinDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }

    // Load persisted values
    val savedSens by prefs.crySensitivity.collectAsState(initial = 0.55f)
    val savedDataSaver by prefs.dataSaver.collectAsState(initial = false)
    val lockEnabled by pinManager.isEnabled.collectAsState(initial = false)
    LaunchedEffect(savedSens) { crySensitivity = savedSens }
    LaunchedEffect(savedDataSaver) { dataSaver = savedDataSaver }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                newPin = ""
            },
            title = { Text("Set a PIN") },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { v -> newPin = v.filter { it.isDigit() }.take(4) },
                    label = { Text("4-digit PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newPin.length == 4,
                    onClick = {
                        val pin = newPin
                        scope.launch { pinManager.setPin(pin) }
                        showPinDialog = false
                        newPin = ""
                    }
                ) { Text("Set PIN") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    newPin = ""
                }) { Text("Cancel") }
            }
        )
    }

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
                    onValueChange = { v ->
                        crySensitivity = v
                        scope.launch { prefs.setCrySensitivity(v) }
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SwitchRow("Auto-start on boot", autoStart) { v ->
                    autoStart = v
                    MonitorController.setAutoStart(context, v)
                }
                Spacer(Modifier.height(8.dp))
                SwitchRow("Data-saver (audio only)", dataSaver) { v ->
                    dataSaver = v
                    scope.launch {
                        prefs.setDataSaver(v)
                        LiveSession.setVideoEnabled(!v)
                    }
                }
                Spacer(Modifier.height(8.dp))
                SwitchRow("App lock (PIN/biometric)", lockEnabled) { v ->
                    if (v) {
                        showPinDialog = true
                    } else {
                        scope.launch { pinManager.disable() }
                    }
                }
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
