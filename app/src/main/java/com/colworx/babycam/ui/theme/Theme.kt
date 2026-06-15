package com.colworx.babycam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

private val DarkColors = darkColorScheme(
    primary = Indigo,
    onPrimary = White,
    background = NightBg,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    error = AlertRed,
)

@Composable
fun BabyCamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BabyCamTypography,
        content = content
    )
}
