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
import com.colworx.babycam.service.MonitorController
import com.colworx.babycam.signaling.RoomToken
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
fun AppNavigation() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPreferences(context) }
    var pendingRole by remember { mutableStateOf(Role.NONE) }

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
                if (pendingRole == Role.BABY) nav.navigate(Routes.BATTERY)
                else nav.navigate(Routes.PARENT_SCAN)
            })
        }
        composable(Routes.BATTERY) {
            BatterySetupScreen(onDone = { nav.navigate(Routes.BABY_PAIRING) })
        }
        composable(Routes.BABY_PAIRING) {
            val room = remember { RoomToken.generate() }
            LaunchedEffect(room) { LiveSession.startBaby(context, room) }
            BabyPairingScreen(
                room = room,
                onContinue = {
                    MonitorController.start(context)
                    nav.navigate(Routes.BABY_ACTIVE)
                },
                onCancel = { LiveSession.stop(); nav.popBackStack() },
            )
        }
        composable(Routes.BABY_ACTIVE) {
            BabyActiveScreen(onStop = {
                LiveSession.stop()
                MonitorController.stop(context)
                nav.popBackStack(Routes.CHOOSE, false)
            })
        }
        composable(Routes.PARENT_SCAN) {
            ParentScanScreen(onScanned = { token ->
                LiveSession.startParent(context, token)
                nav.navigate(Routes.PARENT_LIVE)
            })
        }
        composable(Routes.PARENT_LIVE) {
            ParentLiveScreen()
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
