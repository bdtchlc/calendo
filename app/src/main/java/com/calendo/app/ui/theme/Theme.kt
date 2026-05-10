package com.calendo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = EventFill,
    background = Color(0xFFFDFDFE),
    surface = Color.White,
    onSurface = Color(0xFF1B1F2A),
    outline = TimelineGrid,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BB9FF),
    onPrimary = Color(0xFF0B1533),
    primaryContainer = Color(0xFF1E2F55),
    background = Color(0xFF10131C),
    surface = Color(0xFF171B26),
    onSurface = Color(0xFFE7ECF8),
    outline = Color(0xFF3C465E),
)

@Composable
fun CalendoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
