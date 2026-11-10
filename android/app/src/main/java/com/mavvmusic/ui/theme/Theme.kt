package com.mavvmusic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF684A8A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDF5),
    onPrimaryContainer = Color(0xFF261638),
    secondary = Color(0xFF8C5D79),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4DCE9),
    onSecondaryContainer = Color(0xFF321B2B),
    tertiary = Color(0xFF456B8D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD8E8F7),
    onTertiaryContainer = Color(0xFF14283A),
    background = Color(0xFFF8F5FA),
    onBackground = Color(0xFF28232D),
    surface = Color(0xFFF8F5FA),
    onSurface = Color(0xFF28232D),
    surfaceVariant = Color(0xFFECE6F0),
    onSurfaceVariant = Color(0xFF514A59),
    outline = Color(0xFF817987),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD4B7F2),
    onPrimary = Color(0xFF39234F),
    primaryContainer = Color(0xFF513667),
    onPrimaryContainer = Color(0xFFF0DDFE),
    secondary = Color(0xFFE7BBD4),
    onSecondary = Color(0xFF48293D),
    secondaryContainer = Color(0xFF603D53),
    onSecondaryContainer = Color(0xFFFFD8ED),
    tertiary = Color(0xFFB8D5F0),
    onTertiary = Color(0xFF22384D),
    tertiaryContainer = Color(0xFF354F67),
    onTertiaryContainer = Color(0xFFD7E9FF),
    background = Color(0xFF17141B),
    onBackground = Color(0xFFEAE3EF),
    surface = Color(0xFF211D26),
    onSurface = Color(0xFFEAE3EF),
    surfaceVariant = Color(0xFF39333F),
    onSurfaceVariant = Color(0xFFD0C5D6),
    outline = Color(0xFF988E9E),
)

@Composable
fun MavvMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
