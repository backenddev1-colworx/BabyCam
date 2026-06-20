package com.colworx.babycam.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.colworx.babycam.data.Role
import com.colworx.babycam.ui.components.AppCard
import com.colworx.babycam.ui.components.PrimaryButton
import com.colworx.babycam.ui.components.SecondaryButton
import com.colworx.babycam.ui.theme.BabyCamTheme
import com.colworx.babycam.ui.theme.Indigo900
import com.colworx.babycam.ui.theme.IndigoDeep
import com.colworx.babycam.ui.theme.Lavender100
import com.colworx.babycam.ui.theme.Muted
import com.colworx.babycam.ui.theme.Teal

private fun isGranted(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

@Composable
fun PermissionsScreen(role: Role = Role.NONE, onContinue: () -> Unit) {
    val context = LocalContext.current

    // Camera is REQUIRED for the baby (it streams video). For the parent it's OPTIONAL — only used
    // if the parent later shares its own camera back (on-demand two-way) — so we still request it
    // but never block setup when it's denied.
    val cameraRequired = role != Role.PARENT
    val notificationsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    var cameraGranted by remember {
        mutableStateOf(isGranted(context, Manifest.permission.CAMERA))
    }
    var micGranted by remember {
        mutableStateOf(isGranted(context, Manifest.permission.RECORD_AUDIO))
    }
    var notificationsGranted by remember {
        mutableStateOf(
            if (notificationsSupported)
                isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
            else true
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result[Manifest.permission.CAMERA]?.let { cameraGranted = it }
        result[Manifest.permission.RECORD_AUDIO]?.let { micGranted = it }
        if (notificationsSupported) {
            result[Manifest.permission.POST_NOTIFICATIONS]?.let { notificationsGranted = it }
        }
        // Core requirements: camera (baby only) + mic
        val coreOk = (if (cameraRequired) cameraGranted else true) && micGranted
        if (coreOk) onContinue()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(24.dp)
        ) {
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.titleLarge,
                color = Indigo900
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Needed to monitor safely",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
            Spacer(modifier = Modifier.height(24.dp))

            PermissionRow(
                icon = Icons.Outlined.PhotoCamera,
                label = "Camera",
                description = if (role == Role.PARENT) "Share your camera with baby (optional)"
                              else "See your baby in live video",
                granted = cameraGranted
            )
            Spacer(modifier = Modifier.height(12.dp))
            PermissionRow(
                icon = Icons.Outlined.Mic,
                label = "Microphone",
                description = if (role == Role.PARENT) "Talk to your baby" else "Hear sounds and cries",
                granted = micGranted
            )
            Spacer(modifier = Modifier.height(12.dp))
            PermissionRow(
                icon = Icons.Outlined.Notifications,
                label = "Notifications",
                description = "Get alerts when something happens",
                granted = notificationsGranted
            )

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = "Allow & continue",
                onClick = {
                    val toRequest = mutableListOf<String>()
                    if (!cameraGranted) toRequest.add(Manifest.permission.CAMERA)
                    if (!micGranted) toRequest.add(Manifest.permission.RECORD_AUDIO)
                    if (notificationsSupported && !notificationsGranted) {
                        toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (toRequest.isEmpty()) {
                        val coreOk = (if (cameraRequired) cameraGranted else true) && micGranted
                        if (coreOk) onContinue()
                    } else {
                        launcher.launch(toRequest.toTypedArray())
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    label: String,
    description: String,
    granted: Boolean
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Lavender100, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = IndigoDeep,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Indigo900
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (granted) "Granted" else "Not granted",
                tint = if (granted) Teal else Muted,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun BatterySetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val mfr = Build.MANUFACTURER.lowercase()

    val steps: List<String> = when {
        mfr.contains("xiaomi") || mfr.contains("redmi") || mfr.contains("poco") -> listOf(
            "1. Open Settings, then Apps and find BabyCam.",
            "2. Tap Autostart and turn it ON.",
            "3. Go to Battery saver and set it to No restrictions.",
            "4. Disable Battery optimization for BabyCam."
        )
        mfr.contains("oppo") || mfr.contains("realme") || mfr.contains("vivo") -> listOf(
            "1. Open Settings, then Battery, then App battery management.",
            "2. Find BabyCam and allow background activity.",
            "3. Enable Allow auto-launch for BabyCam.",
            "4. Disable Battery optimization for BabyCam."
        )
        mfr.contains("samsung") -> listOf(
            "1. Open Settings, then Battery and device care, then Battery.",
            "2. Tap Background usage limits.",
            "3. Remove BabyCam from Sleeping and Deep sleeping apps.",
            "4. Add BabyCam to Never sleeping apps."
        )
        mfr.contains("infinix") || mfr.contains("tecno") ||
            mfr.contains("itel") || mfr.contains("transsion") -> listOf(
            "1. Open Settings, then Power Marathon or Power Saving.",
            "2. Allow BabyCam to run in the background.",
            "3. Enable Autostart for BabyCam.",
            "4. Disable Battery optimization for BabyCam."
        )
        else -> listOf(
            "1. Open Settings, then Apps and find BabyCam.",
            "2. Allow Autostart or background activity if available.",
            "3. Open Battery and set BabyCam to Unrestricted.",
            "4. Disable Battery optimization for BabyCam."
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Lavender100, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BatteryChargingFull,
                        contentDescription = null,
                        tint = IndigoDeep,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Keep it always on",
                    style = MaterialTheme.typography.titleLarge,
                    color = Indigo900
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "So monitoring never stops in the background.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
            Spacer(modifier = Modifier.height(24.dp))

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    steps.forEach { step ->
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Indigo900
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            SecondaryButton(
                text = "Open battery settings",
                onClick = {
                    try {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        ).setData(Uri.parse("package:" + context.packageName))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val fallback = Intent(
                                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                            )
                            context.startActivity(fallback)
                        } catch (_: Exception) {
                            // No battery settings activity available on this device.
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            PrimaryButton(
                text = "Done",
                onClick = onDone,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview
@Composable
private fun PermissionsScreenPreview() {
    BabyCamTheme {
        PermissionsScreen(onContinue = {})
    }
}

@Preview
@Composable
private fun BatterySetupScreenPreview() {
    BabyCamTheme {
        BatterySetupScreen(onDone = {})
    }
}
