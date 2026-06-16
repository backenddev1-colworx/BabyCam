package com.colworx.babycam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val White = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = IndigoDeep,
    onPrimary = White,
    primaryContainer = Lavender100,
    onPrimaryContainer = Indigo900,
    secondary = Teal,
    background = Lavender50,
    onBackground = Indigo900,
    surface = White,
    onSurface = Indigo900,
    error = AlertRed,
)

/**
 * Every screen in this app hardcodes the brand's light palette (Lavender/Indigo) directly —
 * only the always-on-dark live-view screen uses the Night* colors, by deliberate design (a
 * camera-viewfinder look), not as a system dark-mode response. There is no real dark theme for
 * the rest of the app.
 *
 * Previously this followed `isSystemInDarkTheme()`, which swapped MaterialTheme's colorScheme to
 * a dark one when the phone was in system dark mode — but only *default Material3 components*
 * (dialogs, switches, dropdowns, text field indicators, etc.) read that scheme; every custom
 * screen kept its hardcoded light backgrounds. The result was dark-styled system widgets sitting
 * on top of light hardcoded screens — a visible color clash ("screen kharab"). Pinning to
 * [LightColors] unconditionally keeps the whole app visually consistent regardless of the
 * phone's system theme setting.
 */
@Composable
fun BabyCamTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = BabyCamTypography,
        content = content
    )
}
