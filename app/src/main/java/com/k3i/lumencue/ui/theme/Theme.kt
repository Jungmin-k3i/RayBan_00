package com.k3i.lumencue.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF62D6C4),
    secondary = Color(0xFF8AB4F8),
    tertiary = Color(0xFFE85D75),
    background = Color(0xFF101113),
    surface = Color(0xFF171A20),
    surfaceVariant = Color(0xFF20242D),
    onPrimary = Color(0xFF071311),
    onSecondary = Color(0xFF06121E),
    onTertiary = Color.White,
    onBackground = Color(0xFFE5E7EB),
    onSurface = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFFB8BDC7)
)

@Composable
fun LumenCueTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
