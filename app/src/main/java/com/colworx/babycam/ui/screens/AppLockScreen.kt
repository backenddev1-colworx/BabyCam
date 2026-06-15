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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.colworx.babycam.security.BiometricAuth
import com.colworx.babycam.security.PinManager
import com.colworx.babycam.ui.theme.BabyCamTheme
import com.colworx.babycam.ui.theme.Indigo900
import com.colworx.babycam.ui.theme.IndigoDeep
import com.colworx.babycam.ui.theme.Lavender100
import com.colworx.babycam.ui.theme.Lavender50
import com.colworx.babycam.ui.theme.Muted
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 4

@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pinManager = remember { PinManager(context) }

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    // Resolve a FragmentActivity for biometric prompts; null if unavailable.
    val activity = remember { context as? FragmentActivity }
    val biometricAvailable = remember {
        try {
            activity != null && BiometricAuth.canAuthenticate(context)
        } catch (e: Exception) {
            false
        }
    }

    fun submit(candidate: String) {
        scope.launch {
            if (pinManager.verify(candidate)) {
                onUnlocked()
            } else {
                error = true
                pin = ""
            }
        }
    }

    fun onDigit(digit: String) {
        if (pin.length >= PIN_LENGTH) return
        error = false
        pin += digit
        if (pin.length == PIN_LENGTH) submit(pin)
    }

    fun onBackspace() {
        error = false
        if (pin.isNotEmpty()) pin = pin.dropLast(1)
    }

    fun onFingerprint() {
        val act = activity ?: return
        try {
            BiometricAuth.authenticate(
                activity = act,
                onSuccess = { onUnlocked() },
                onError = { /* keep PIN entry available */ },
            )
        } catch (e: Exception) {
            // Biometric unavailable at runtime — fall back to PIN silently.
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Lavender50)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Lavender100),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = IndigoDeep,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Enter PIN",
                color = Indigo900,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Unlock BabyCam",
                color = Muted,
                fontSize = 14.sp,
            )

            Spacer(modifier = Modifier.height(28.dp))

            PinDots(filled = pin.length, error = error)

            Spacer(modifier = Modifier.height(32.dp))

            NumberPad(
                onDigit = ::onDigit,
                onBackspace = ::onBackspace,
            )

            if (biometricAvailable) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = ::onFingerprint) {
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = null,
                        tint = IndigoDeep,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Use fingerprint",
                        color = IndigoDeep,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PinDots(filled: Int, error: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(PIN_LENGTH) { index ->
            val isFilled = index < filled
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            error -> Color(0xFFD96C6C)
                            isFilled -> IndigoDeep
                            else -> Lavender100
                        }
                    ),
            )
        }
    }
}

@Composable
private fun NumberPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "<"),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(modifier = Modifier.size(64.dp))
                        "<" -> PadButton(onClick = onBackspace) {
                            Icon(
                                imageVector = Icons.Outlined.Backspace,
                                contentDescription = "Delete",
                                tint = IndigoDeep,
                            )
                        }
                        else -> PadButton(onClick = { onDigit(key) }) {
                            Text(
                                text = key,
                                color = Indigo900,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Lavender100),
    ) {
        content()
    }
}

@Preview(showBackground = true)
@Composable
private fun AppLockScreenPreview() {
    BabyCamTheme {
        AppLockScreen(onUnlocked = {})
    }
}
