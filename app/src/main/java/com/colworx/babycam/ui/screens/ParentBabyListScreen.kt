package com.colworx.babycam.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.colworx.babycam.data.SavedBaby
import com.colworx.babycam.signaling.PresenceChecker
import com.colworx.babycam.ui.components.AppCard
import com.colworx.babycam.ui.components.StatusPill
import com.colworx.babycam.ui.theme.Indigo900
import com.colworx.babycam.ui.theme.IndigoDeep
import com.colworx.babycam.ui.theme.Lavender100
import com.colworx.babycam.ui.theme.Lavender50
import com.colworx.babycam.ui.theme.Muted
import com.colworx.babycam.ui.theme.Teal
import com.colworx.babycam.ui.theme.TealBg

private enum class Presence { CHECKING, ONLINE, OFFLINE }

/**
 * Parent's hub when more than one baby is paired: a list of all saved babies with a live
 * online/offline status (checked via a short MQTT ping, not a held connection — see
 * [PresenceChecker]) and the time each was last viewed. Tapping a card connects to that baby.
 */
@Composable
fun ParentBabyListScreen(
    babies: List<SavedBaby>,
    onSelectBaby: (SavedBaby) -> Unit,
    onAddBaby: () -> Unit,
    onSettings: () -> Unit,
) {
    val presence = remember { mutableStateMapOf<String, Presence>() }

    LaunchedEffect(babies) {
        babies.forEach { baby ->
            presence[baby.room] = Presence.CHECKING
            PresenceChecker.check(baby.room) { online ->
                presence[baby.room] = if (online) Presence.ONLINE else Presence.OFFLINE
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Lavender50)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your babies",
                style = MaterialTheme.typography.titleLarge,
                color = Indigo900,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAddBaby) {
                Icon(Icons.Outlined.AddCircle, contentDescription = "Add baby", tint = IndigoDeep)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = IndigoDeep)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (babies.isEmpty()) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "No babies paired yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(babies, key = { it.room }) { baby ->
                    BabyCard(
                        baby = baby,
                        presence = presence[baby.room] ?: Presence.CHECKING,
                        onClick = { onSelectBaby(baby) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BabyCard(baby: SavedBaby, presence: Presence, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Lavender100, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.ChildCare, contentDescription = null, tint = IndigoDeep)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(baby.name, style = MaterialTheme.typography.bodyLarge, color = Indigo900)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = relativeTime(baby.lastActiveAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
            Spacer(Modifier.width(8.dp))
            when (presence) {
                Presence.CHECKING -> StatusPill("Checking…", Lavender100, Muted)
                Presence.ONLINE -> StatusPill("Online", TealBg, Teal)
                Presence.OFFLINE -> StatusPill("Offline", Lavender100, Muted)
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = IndigoDeep)
        }
    }
}

private fun relativeTime(epochMs: Long): String {
    if (epochMs <= 0L) return "Never viewed"
    val diffMs = System.currentTimeMillis() - epochMs
    val minutes = diffMs / 60_000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} hr ago"
        else -> "${minutes / (24 * 60)} day(s) ago"
    }
}
