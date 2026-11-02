package com.gdmusic.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF3DDC97)
private val AccentDark = Color(0xFF1E9E6A)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF08281C),
    secondary = Color(0xFF9BB3C7),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE8EDF2),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE8EDF2),
    surfaceVariant = Color(0xFF1E252E),
    onSurfaceVariant = Color(0xFFADBAC7),
    outline = Color(0xFF3A4552)
)

private val LightColors = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    secondary = Color(0xFF4A6572),
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF10151A),
    surface = Color.White,
    onSurface = Color(0xFF10151A),
    surfaceVariant = Color(0xFFE8EDF2),
    onSurfaceVariant = Color(0xFF46525D),
    outline = Color(0xFFC3CCD6)
)

@Composable
fun GdTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
