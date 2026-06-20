package com.colworx.babycam.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.colworx.babycam.data.AppPreferences
import com.colworx.babycam.data.Role
import com.colworx.babycam.security.PinManager
import com.colworx.babycam.service.MonitorController
import com.colworx.babycam.service.ParentMonitorController
import com.colworx.babycam.ui.screens.AppLockScreen
import com.colworx.babycam.ui.screens.BabyActiveScreen
import com.colworx.babycam.ui.screens.BabyPairingScreen
import com.colworx.babycam.ui.screens.BatterySetupScreen
import com.colworx.babycam.ui.screens.ChooseDeviceScreen
import com.colworx.babycam.ui.screens.ParentBabyListScreen
import com.colworx.babycam.ui.screens.ParentLiveScreen
import com.colworx.babycam.ui.screens.ParentScanScreen
import com.colworx.babycam.ui.screens.PermissionsScreen
import com.colworx.babycam.ui.screens.SettingsScreen
import com.colworx.babycam.ui.screens.SnapshotGalleryScreen
import com.colworx.babycam.ui.screens.WelcomeScreen
import com.colworx.babycam.webrtc.LiveSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Routes {
    const val WELCOME = "welcome"
    const val CHOOSE = "choose"
    const val PERMISSIONS = "permissions"
    const val BATTERY = "battery"
    const val BABY_PAIRING = "baby_pairing"
    const val BABY_ACTIVE = "baby_active"
    const val PARENT_SCAN = "parent_scan"
    const val PARENT_BABIES = "parent_babies"
    const val PARENT_LIVE = "parent_live"
    const val SETTINGS = "settings"
    const val SNAPSHOTS = "snapshots"
}

@Composable
fun AppNavigation(startAtParentLive: Boolean = false) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPreferences(context) }
    val pinManager = remember { PinManager(context) }
    var pendingRole by remember { mutableStateOf(Role.NONE) }
    var locked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        pinManager.isEnabled.collect { enabled -> locked = enabled }
    }

    // Cry-alert deep link: jump straight to the live view if a pairing is remembered.
    // Otherwise, resume exactly where the user left off before a hard kill (state persistence).
    LaunchedEffect(startAtParentLive) {
        if (startAtParentLive) {
            val savedRoom = prefs.parentRoom.first()
            if (savedRoom != null) {
                prefs.setLastVisitedView(Routes.PARENT_LIVE)
                val micMuted = prefs.savedBabies.first().firstOrNull { it.room == savedRoom }?.micMuted ?: false
                LiveSession.startParent(context, savedRoom, initialMicOn = !micMuted)
                ParentMonitorController.start(context)
                nav.navigate(Routes.PARENT_LIVE)
            }
            return@LaunchedEffect
        }
        try {
            prefs.migrateLegacyParentRoomIfNeeded()
            val role = prefs.role.first()
            val lastView = prefs.lastVisitedView.first()
            val babySetupDone = prefs.babySetupDone.first()
            when {
                // An already-set-up baby reopens straight to its monitor/status screen — no
                // welcome / permissions / pairing walkthrough again (it shows the code + parent
                // status there). Also covers a hard-kill resume (lastView == BABY_ACTIVE).
                role == Role.BABY && (babySetupDone || lastView == Routes.BABY_ACTIVE) -> {
                    val room = prefs.babyRoomOnce()
                    LiveSession.startBaby(context, room)
                    MonitorController.start(context)
                    nav.navigate(Routes.BABY_ACTIVE) { popUpTo(Routes.WELCOME) { inclusive = false } }
                }
                role == Role.PARENT && lastView == Routes.PARENT_LIVE -> {
                    val room = prefs.parentRoom.first()
                    if (room != null) {
                        val micMuted = prefs.savedBabies.first().firstOrNull { it.room == room }?.micMuted ?: false
                        LiveSession.startParent(context, room, initialMicOn = !micMuted)
                        ParentMonitorController.start(context)
                        nav.navigate(Routes.PARENT_LIVE) { popUpTo(Routes.WELCOME) { inclusive = false } }
                    } else {
                        prefs.setLastVisitedView(null)
                    }
                }
            }
        } catch (_: Exception) {
            // Saved state missing/corrupt — fall back to the normal welcome flow rather than crash.
        }
    }

    if (locked) {
        AppLockScreen(onUnlocked = { locked = false })
        return
    }

    NavHost(navController = nav, startDestination = Routes.WELCOME) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onGetStarted = { nav.navigate(Routes.CHOOSE) })
        }
        composable(Routes.CHOOSE) {
            ChooseDeviceScreen(
                onChooseBaby = {
                    pendingRole = Role.BABY
                    scope.launch { prefs.setRole(Role.BABY) }
                    nav.navigate(Routes.PERMISSIONS)
                },
                onChooseParent = {
                    pendingRole = Role.PARENT
                    scope.launch { prefs.setRole(Role.PARENT) }
                    nav.navigate(Routes.PERMISSIONS)
                },
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(role = pendingRole, onContinue = {
                if (pendingRole == Role.BABY) {
                    nav.navigate(Routes.BATTERY)
                } else {
                    // Parent: jump to the babies dashboard if any are saved, else first pairing.
                    scope.launch {
                        val babies = prefs.savedBabies.first()
                        if (babies.isNotEmpty()) {
                            nav.navigate(Routes.PARENT_BABIES)
                        } else {
                            nav.navigate(Routes.PARENT_SCAN)
                        }
                    }
                }
            })
        }
        composable(Routes.BATTERY) {
            BatterySetupScreen(onDone = { nav.navigate(Routes.BABY_PAIRING) })
        }
        composable(Routes.BABY_PAIRING) {
            // Reuse the persisted baby room so the pairing token survives restarts.
            var room by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) { room = prefs.babyRoomOnce() }
            val loadedRoom = room
            if (loadedRoom != null) {
                LaunchedEffect(loadedRoom) { LiveSession.startBaby(context, loadedRoom) }
                BabyPairingScreen(
                    room = loadedRoom,
                    onContinue = {
                        scope.launch {
                            prefs.setLastVisitedView(Routes.BABY_ACTIVE)
                            // Mark first-time setup complete so future launches skip straight to
                            // the monitor screen instead of re-running the setup walkthrough.
                            prefs.setBabySetupDone(true)
                        }
                        MonitorController.start(context)
                        nav.navigate(Routes.BABY_ACTIVE)
                    },
                    onCancel = { LiveSession.stop(); nav.popBackStack() },
                )
            }
        }
        composable(Routes.BABY_ACTIVE) {
            BabyActiveScreen(
                onStop = {
                    scope.launch { prefs.setLastVisitedView(null) }
                    LiveSession.stop()
                    MonitorController.stop(context)
                    nav.popBackStack(Routes.CHOOSE, false)
                },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.PARENT_SCAN) {
            ParentScanScreen(onScanned = { token ->
                scope.launch {
                    prefs.upsertBaby(token, "Baby ${prefs.savedBabies.first().size + 1}", System.currentTimeMillis())
                    prefs.setParentRoom(token)
                    prefs.setLastVisitedView(Routes.PARENT_LIVE)
                }
                LiveSession.startParent(context, token)
                ParentMonitorController.start(context)
                nav.navigate(Routes.PARENT_LIVE) {
                    popUpTo(Routes.PARENT_SCAN) { inclusive = true }
                }
            })
        }
        composable(Routes.PARENT_BABIES) {
            val babies by prefs.savedBabies.collectAsState(initial = emptyList())
            ParentBabyListScreen(
                babies = babies,
                onSelectBaby = { baby ->
                    scope.launch {
                        prefs.setParentRoom(baby.room)
                        prefs.touchBaby(baby.room, System.currentTimeMillis())
                        prefs.setLastVisitedView(Routes.PARENT_LIVE)
                    }
                    LiveSession.startParent(context, baby.room, initialMicOn = !baby.micMuted)
                    ParentMonitorController.start(context)
                    nav.navigate(Routes.PARENT_LIVE)
                },
                onAddBaby = { nav.navigate(Routes.PARENT_SCAN) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onRenameBaby = { room, newName ->
                    scope.launch { prefs.renameBaby(room, newName) }
                },
            )
        }

        composable(Routes.PARENT_LIVE) {
            ParentLiveScreen(
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenSnapshots = { nav.navigate(Routes.SNAPSHOTS) },
                onDisconnect = {
                    scope.launch { prefs.setLastVisitedView(null) }
                    LiveSession.stop()
                    ParentMonitorController.stop(context)
                    nav.navigate(Routes.PARENT_BABIES) {
                        popUpTo(Routes.PARENT_LIVE) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.SNAPSHOTS) {
            SnapshotGalleryScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onForgetPairing = {
                    scope.launch {
                        val activeRoom = prefs.parentRoom.first()
                        if (activeRoom != null) prefs.removeBaby(activeRoom)
                        prefs.clearParentRoom()
                        prefs.setLastVisitedView(null)
                    }
                    LiveSession.stop()
                    ParentMonitorController.stop(context)
                    nav.navigate(Routes.PARENT_BABIES) {
                        popUpTo(Routes.WELCOME) { inclusive = false }
                    }
                }
            )
        }
    }
}
