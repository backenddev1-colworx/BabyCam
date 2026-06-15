package com.colworx.babycam.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.colworx.babycam.ui.screens.AppLockScreen
import com.colworx.babycam.ui.screens.BabyActiveScreen
import com.colworx.babycam.ui.screens.BabyPairingScreen
import com.colworx.babycam.ui.screens.BatterySetupScreen
import com.colworx.babycam.ui.screens.ChooseDeviceScreen
import com.colworx.babycam.ui.screens.ParentLiveScreen
import com.colworx.babycam.ui.screens.ParentScanScreen
import com.colworx.babycam.ui.screens.PermissionsScreen
import com.colworx.babycam.ui.screens.SettingsScreen
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
    const val PARENT_LIVE = "parent_live"
    const val SETTINGS = "settings"
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
    LaunchedEffect(startAtParentLive) {
        if (startAtParentLive) {
            val savedRoom = prefs.parentRoom.first()
            if (savedRoom != null) {
                LiveSession.startParent(context, savedRoom)
                nav.navigate(Routes.PARENT_LIVE)
            }
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
            PermissionsScreen(onContinue = {
                if (pendingRole == Role.BABY) {
                    nav.navigate(Routes.BATTERY)
                } else {
                    // Parent: skip scanning if a pairing is already remembered.
                    scope.launch {
                        val savedRoom = prefs.parentRoom.first()
                        if (savedRoom != null) {
                            LiveSession.startParent(context, savedRoom)
                            nav.navigate(Routes.PARENT_LIVE)
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
                    LiveSession.stop()
                    MonitorController.stop(context)
                    nav.popBackStack(Routes.CHOOSE, false)
                },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.PARENT_SCAN) {
            ParentScanScreen(onScanned = { token ->
                scope.launch { prefs.setParentRoom(token) }
                LiveSession.startParent(context, token)
                nav.navigate(Routes.PARENT_LIVE)
            })
        }

        composable(Routes.PARENT_LIVE) {
            ParentLiveScreen(
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onDisconnect = {
                    LiveSession.stop()
                    nav.navigate(Routes.CHOOSE) {
                        popUpTo(Routes.WELCOME) { inclusive = false }
                    }
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
