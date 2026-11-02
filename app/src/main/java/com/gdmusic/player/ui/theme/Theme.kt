package com.gdmusic.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeteaseRed = Color(0xFFD33A31)
private val NeteaseRedLight = Color(0xFFE2554B)

private val DarkColors = darkColorScheme(
    primary = NeteaseRedLight,
    onPrimary = Color.White,
    secondary = Color(0xFFE7A0A0),
    onSecondary = Color(0xFF2B0705),
    background = Color(0xFF101012),
    onBackground = Color(0xFFF2F2F3),
    surface = Color(0xFF17171A),
    onSurface = Color(0xFFF2F2F3),
    surfaceVariant = Color(0xFF232327),
    onSurfaceVariant = Color(0xFFAFB0B6),
    outline = Color(0xFF3C3C42)
)

private val LightColors = lightColorScheme(
    primary = NeteaseRed,
    onPrimary = Color.White,
    secondary = Color(0xFF7A5C5A),
    onSecondary = Color.White,
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF18181A),
    surface = Color.White,
    onSurface = Color(0xFF18181A),
    surfaceVariant = Color(0xFFF0F0F2),
    onSurfaceVariant = Color(0xFF5A5A61),
    outline = Color(0xFFC8C8CE)
)

@Composable
fun GdTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
