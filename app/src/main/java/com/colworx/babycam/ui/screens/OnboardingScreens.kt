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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.colworx.babycam.ui.components.AppCard
import com.colworx.babycam.ui.components.PrimaryButton
import com.colworx.babycam.ui.theme.BabyCamTheme
import com.colworx.babycam.ui.theme.Indigo900
import com.colworx.babycam.ui.theme.IndigoDeep
import com.colworx.babycam.ui.theme.Lavender100
import com.colworx.babycam.ui.theme.Lavender50
import com.colworx.babycam.ui.theme.Muted
import com.colworx.babycam.ui.theme.Teal
import com.colworx.babycam.ui.theme.TealBg

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Lavender50)
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Lavender100, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChildCare,
                    contentDescription = "BabyCam logo",
                    tint = IndigoDeep,
                    modifier = Modifier.size(46.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "BabyCam",
                style = MaterialTheme.typography.headlineMedium,
                color = Indigo900
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Watch over your little one",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
        }
        PrimaryButton(
            text = "Get started",
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ChooseDeviceScreen(
    onChooseBaby: () -> Unit,
    onChooseParent: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Lavender50)
            .safeDrawingPadding()
            .padding(18.dp)
    ) {
        Text(
            text = "Choose this device",
            style = MaterialTheme.typography.titleLarge,
            color = Indigo900
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "What will this phone do?",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )
        Spacer(modifier = Modifier.height(18.dp))

        // Card 1 — Baby unit (highlighted with a 2.dp IndigoDeep border).
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, IndigoDeep, RoundedCornerShape(16.dp))
                .clickable { onChooseBaby() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            DeviceCardContent(
                iconBg = Lavender100,
                iconTint = IndigoDeep,
                icon = Icons.Outlined.PhotoCamera,
                iconDescription = "Baby unit",
                title = "Baby unit",
                subtitle = "Stays with baby. Camera & mic on."
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 2 — Parent unit.
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChooseParent() }
        ) {
            DeviceCardContent(
                iconBg = TealBg,
                iconTint = Teal,
                icon = Icons.Outlined.Visibility,
                iconDescription = "Parent unit",
                title = "Parent unit",
                subtitle = "Watch & listen from here."
            )
        }
    }
}

@Composable
private fun DeviceCardContent(
    iconBg: Color,
    iconTint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconDescription: String,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(iconBg, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Indigo900
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    BabyCamTheme {
        WelcomeScreen(onGetStarted = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ChooseDeviceScreenPreview() {
    BabyCamTheme {
        ChooseDeviceScreen(onChooseBaby = {}, onChooseParent = {})
    }
}
