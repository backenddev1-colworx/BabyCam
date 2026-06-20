package com.colworx.babycam.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlipCameraAndroid
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import com.colworx.babycam.data.Role
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
import com.colworx.babycam.ui.theme.Teal
import com.colworx.babycam.ui.theme.TealBg
import com.colworx.babycam.webrtc.LiveSession
import com.colworx.babycam.webrtc.VideoRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 1) Parent scan screen ------------------------------------------------------

@Composable
fun ParentScanScreen(onScanned: (String) -> Unit = {}) {
    var code by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(code) {
        if (code.length == 4 && RoomToken.isValid(code)) onScanned(code)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBg)
            .safeDrawingPadding()
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

/** Coarse connection-quality tiers shown to the parent (no per-packet stats — see note below). */
private enum class SignalQuality { STRONG, RECONNECTING, OFFLINE }

// 2) Parent live screen ------------------------------------------------------

@Composable
fun ParentLiveScreen(
    onSettings: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onOpenSnapshots: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    val track by LiveSession.remoteVideo
    val connState by LiveSession.connState
    val signalingUp by LiveSession.signalingUp
    val connection = LiveSession.connection
    val battery by LiveSession.babyBattery
    val babyCamOn by LiveSession.babyCamEnabled
    val babyMicOn by LiveSession.babyMicEnabled
    val torchOn by LiveSession.babyTorchOn

    var nightMode by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    val parentSharing by LiveSession.parentSharingCamera
    val talking by LiveSession.parentTalking
    val bellRinging by LiveSession.babyLullabyPlaying

    // Persisted night-mode + capture quality so they survive leaving/reopening the live view.
    // Quality is (re)sent to the baby whenever signaling comes up, so it sticks across reconnects.
    val savedNight by prefs.nightMode.collectAsState(initial = false)
    LaunchedEffect(savedNight) { nightMode = savedNight }
    val savedQuality by prefs.videoQuality.collectAsState(initial = "HIGH")
    LaunchedEffect(signalingUp, savedQuality) {
        if (signalingUp) LiveSession.setRemoteQuality(savedQuality == "SAVER")
    }

    // Pinch-to-zoom on the live feed: scale 1x-4x, drag-to-pan while zoomed in, double-tap to
    // reset. Resets automatically when the video track changes (e.g. reconnect) so a fresh
    // stream always starts un-zoomed.
    var zoomScale by remember { mutableStateOf(1f) }
    var zoomOffsetX by remember { mutableStateOf(0f) }
    var zoomOffsetY by remember { mutableStateOf(0f) }
    LaunchedEffect(track) {
        zoomScale = 1f
        zoomOffsetX = 0f
        zoomOffsetY = 0f
    }

    // Elapsed time since screen opened
    var elapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    // Connection-quality pill. This is derived only from ICE state + signaling reachability, not
    // from per-packet stats (bitrate/loss) — wiring up WebRTC getStats() for a true "Weak" tier
    // is a reasonable follow-up but adds real complexity/risk for a first cut.
    val quality = when {
        !signalingUp -> SignalQuality.RECONNECTING
        connState == org.webrtc.PeerConnection.IceConnectionState.CONNECTED ||
            connState == org.webrtc.PeerConnection.IceConnectionState.COMPLETED -> SignalQuality.STRONG
        connState == org.webrtc.PeerConnection.IceConnectionState.FAILED ||
            connState == org.webrtc.PeerConnection.IceConnectionState.CLOSED -> SignalQuality.OFFLINE
        else -> SignalQuality.RECONNECTING // NEW / CHECKING / DISCONNECTED / null (still connecting)
    }

    // Debounce the full-screen "Connection lost" overlay so a brief blip (a few seconds of ICE
    // re-checking) just shows the small Reconnecting pill instead of jarringly blacking out the
    // whole screen — only truly prolonged offline state takes over the view.
    var showLostOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(quality) {
        if (quality == SignalQuality.OFFLINE) {
            delay(4000L)
            showLostOverlay = true
        } else {
            showLostOverlay = false
        }
    }

    // Intercept back button — require explicit disconnect
    BackHandler { showDisconnectDialog = true }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Stop monitoring?") },
            text = { Text("This will disconnect from the baby camera.") },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectDialog = false
                    onDisconnect()
                }) {
                    Text("Disconnect", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Keep watching")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBg)
    ) {
        // Full-screen video feed
        if (connection != null) {
            VideoRenderer(
                track = track,
                eglContext = connection.eglContext,
                modifier = Modifier
                    .fillMaxSize()
                    .nightModeFilter(nightMode)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                            zoomScale = newScale
                            if (newScale <= 1f) {
                                zoomOffsetX = 0f
                                zoomOffsetY = 0f
                            } else {
                                zoomOffsetX += pan.x
                                zoomOffsetY += pan.y
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            zoomScale = 1f
                            zoomOffsetX = 0f
                            zoomOffsetY = 0f
                        })
                    }
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale,
                        translationX = zoomOffsetX,
                        translationY = zoomOffsetY,
                    )
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

        // Connection-lost overlay (debounced — see showLostOverlay)
        if (showLostOverlay) {
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

        // ── Top status bar ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusPill(
                text = "LIVE",
                bg = Color(0x2EE24B4A),
                fg = Color(0xFFFF8B8A)
            )
            StatusPill(
                text = when (quality) {
                    SignalQuality.STRONG -> "Strong"
                    SignalQuality.RECONNECTING -> "Reconnecting…"
                    SignalQuality.OFFLINE -> "Offline"
                },
                bg = when (quality) {
                    SignalQuality.STRONG -> TealBg
                    SignalQuality.RECONNECTING -> Color(0x33FFA726)
                    SignalQuality.OFFLINE -> Color(0x33FF6B6B)
                },
                fg = when (quality) {
                    SignalQuality.STRONG -> Teal
                    SignalQuality.RECONNECTING -> Color(0xFFFFA726)
                    SignalQuality.OFFLINE -> Color(0xFFFF6B6B)
                }
            )
            Icon(
                imageVector = Icons.Outlined.BatteryStd,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = NightText
            )
            Text(
                text = if (battery != null) "${battery}%" else "--",
                style = MaterialTheme.typography.labelSmall,
                color = NightText
            )
            if (elapsedSeconds > 0) {
                Text(
                    text = "· %02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                    style = MaterialTheme.typography.labelSmall,
                    color = NightText
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = NightText)
            }
            IconButton(
                onClick = { showDisconnectDialog = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.CallEnd,
                    contentDescription = "Disconnect",
                    tint = Color(0xFFFF6B6B)
                )
            }
        }

        // ── Bottom control panel ──────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xEE070714))
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 6.dp)
        ) {
            // Section label: Baby Phone
            Text(
                text = "BABY PHONE",
                color = Color(0x88FFFFFF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
            )

            // Row 1 — remote baby controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundControl(
                    icon = if (babyCamOn) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
                    label = if (babyCamOn) "Cam ON" else "Cam OFF",
                    onClick = { LiveSession.setRemoteCamera(!babyCamOn) },
                    bg = if (babyCamOn) Color(0x33FFFFFF) else Color(0x44FF4444),
                    fg = if (babyCamOn) Color.White else Color(0xFFFF8080)
                )
                RoundControl(
                    icon = if (babyMicOn) Icons.Outlined.Mic else Icons.Outlined.MicOff,
                    label = if (babyMicOn) "Mic ON" else "Mic OFF",
                    onClick = {
                        val newOn = !babyMicOn
                        LiveSession.setRemoteMic(newOn)
                        val room = LiveSession.room
                        if (room.isNotEmpty()) {
                            scope.launch { prefs.setBabyMicMuted(room, muted = !newOn) }
                        }
                    },
                    bg = if (babyMicOn) Color(0x33FFFFFF) else Color(0x44FF4444),
                    fg = if (babyMicOn) Color.White else Color(0xFFFF8080)
                )
                RoundControl(
                    icon = if (torchOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                    label = if (torchOn) "Torch ON" else "Torch",
                    onClick = { LiveSession.setTorch(!torchOn) },
                    bg = if (torchOn) Color(0xFF2D2500) else Color(0x33FFFFFF),
                    fg = if (torchOn) Color(0xFFFFD54F) else NightText
                )
                RoundControl(
                    icon = Icons.Outlined.FlipCameraAndroid,
                    label = "Flip Cam",
                    onClick = { LiveSession.switchCamera() }
                )
            }

            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(0.5.dp)
                    .background(Color(0x33FFFFFF))
            )
            Spacer(Modifier.height(8.dp))

            // Row 2 — parent controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundControl(
                    icon = Icons.Outlined.RecordVoiceOver,
                    label = if (talking) "Talking" else "Talk",
                    onClick = {
                        LiveSession.setTalking(!talking)
                    },
                    size = 52,
                    bg = if (talking) IndigoDeep else Color(0x33FFFFFF),
                    fg = Color.White
                )
                RoundControl(
                    icon = Icons.Outlined.DarkMode,
                    label = if (nightMode) "Night ON" else "Night",
                    onClick = {
                        val v = !nightMode
                        nightMode = v
                        scope.launch { prefs.setNightMode(v) }
                    },
                    bg = if (nightMode) Color(0xFF1D3320) else Color(0x33FFFFFF),
                    fg = if (nightMode) Color(0xFF4CAF50) else NightText
                )
                RoundControl(
                    icon = if (bellRinging) Icons.Outlined.NotificationsActive
                           else Icons.Outlined.Notifications,
                    label = if (bellRinging) "Bell ON" else "Ring bell",
                    onClick = {
                        LiveSession.sendLullaby(if (bellRinging) "stop" else "bell")
                    },
                    bg = if (bellRinging) Color(0xFF3A2D00) else Color(0x33FFFFFF),
                    fg = if (bellRinging) Color(0xFFFFD54F) else NightText
                )
                RoundControl(
                    icon = Icons.Outlined.PhotoCamera,
                    label = "Snapshot",
                    onClick = {
                        LiveSession.captureSnapshot(context) { ok ->
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
                RoundControl(
                    icon = Icons.Outlined.PhotoLibrary,
                    label = "Gallery",
                    onClick = onOpenSnapshots,
                )
                RoundControl(
                    icon = if (parentSharing) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
                    label = if (parentSharing) "Sharing" else "Share Cam",
                    onClick = { LiveSession.setParentCameraSharing(!parentSharing) },
                    bg = if (parentSharing) IndigoDeep else Color(0x33FFFFFF),
                    fg = Color.White
                )
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

// 4) Settings screen ---------------------------------------------------------

@Composable
fun SettingsScreen(onBack: () -> Unit = {}, onForgetPairing: (() -> Unit)? = null) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val pinManager = remember { PinManager(context) }
    val scope = rememberCoroutineScope()

    var crySensitivity by remember { mutableFloatStateOf(0.55f) }
    var autoStart by remember { mutableStateOf(MonitorController.isAutoStartEnabled(context)) }
    var dataSaver by remember { mutableStateOf(false) }

    var showPinDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showForgetDialog by remember { mutableStateOf(false) }

    val savedSens by prefs.crySensitivity.collectAsState(initial = 0.55f)
    val savedDataSaver by prefs.dataSaver.collectAsState(initial = false)
    val lockEnabled by pinManager.isEnabled.collectAsState(initial = false)
    val savedRoom by prefs.parentRoom.collectAsState(initial = null)
    LaunchedEffect(savedSens) { crySensitivity = savedSens }
    LaunchedEffect(savedDataSaver) { dataSaver = savedDataSaver }

    // Settings is shared by both roles; show the controls relevant to each.
    val role by prefs.role.collectAsState(initial = Role.NONE)
    val notifEnabled by prefs.notificationsEnabled.collectAsState(initial = false)
    val cryEnabledBaby by prefs.cryDetectionEnabled.collectAsState(initial = false)
    val cryEnabledRemote by LiveSession.babyCryDetectionEnabled
    val savedQuality by prefs.videoQuality.collectAsState(initial = "HIGH")

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About BabyCam") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Version 1.0", style = MaterialTheme.typography.bodyMedium, color = Indigo900)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Free, open-source baby monitor. No accounts, no subscriptions. " +
                        "Live video, two-way audio, cry alerts, and lullabies — all on your own phones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            }
        )
    }

    if (showForgetDialog) {
        AlertDialog(
            onDismissRequest = { showForgetDialog = false },
            title = { Text("Forget pairing?") },
            text = { Text("This will remove the saved pairing. You will need to enter a new code to reconnect.") },
            confirmButton = {
                TextButton(onClick = {
                    showForgetDialog = false
                    scope.launch { prefs.clearParentRoom() }
                    onForgetPairing?.invoke()
                }) { Text("Forget", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = {
                TextButton(onClick = { showForgetDialog = false }) { Text("Cancel") }
            }
        )
    }

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
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            color = Indigo900
        )
        Spacer(Modifier.height(16.dp))

        // Alerts & cry detection — both default OFF, opt-in here.
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SwitchRow("Notifications (cry & low-battery)", notifEnabled) { v ->
                    scope.launch { prefs.setNotificationsEnabled(v) }
                }
                Spacer(Modifier.height(8.dp))
                if (role == Role.PARENT) {
                    SwitchRow("Baby cry detection (remote)", cryEnabledRemote) { v ->
                        LiveSession.setRemoteCryDetection(v)
                    }
                } else {
                    Text(
                        text = if (cryEnabledBaby) {
                            "Cry detection: ON by parent"
                        } else {
                            "Cry detection: OFF · controlled by parent"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (cryEnabledBaby) Teal else Muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Battery-saver video lever (parent-controlled; reduces the baby's encode/ISP load).
        if (role == Role.PARENT) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SwitchRow("Battery-saver video (low-res)", savedQuality == "SAVER") { v ->
                        scope.launch { prefs.setVideoQuality(if (v) "SAVER" else "HIGH") }
                        LiveSession.setRemoteQuality(v)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Cry sensitivity only matters where detection actually runs — the baby device.
        if (role != Role.PARENT) {
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
        }

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SwitchRow("Auto-start on boot", autoStart) { v ->
                    autoStart = v
                    MonitorController.setAutoStart(context, v)
                }
                if (role == Role.PARENT) {
                    Spacer(Modifier.height(8.dp))
                    SwitchRow("Data-saver (audio only)", dataSaver) { v ->
                        dataSaver = v
                        scope.launch { prefs.setDataSaver(v) }
                        // Real power-save: actually stop the baby's camera capturer.
                        LiveSession.setRemoteCamera(!v)
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
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAboutDialog = true },
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

                if (onForgetPairing != null && savedRoom != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showForgetDialog = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LinkOff,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Forget pairing",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFFF6B6B),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
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
private fun SettingsScreenPreview() {
    BabyCamTheme { SettingsScreen() }
}
