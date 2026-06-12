package com.k3i.rayban_00.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF087C70),
    secondary = Color(0xFF2467A6),
    tertiary = Color(0xFFC83D5B),
    background = Color(0xFFF5F7FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EDF3),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF16181D),
    onSurface = Color(0xFF16181D),
    onSurfaceVariant = Color(0xFF4B5563)
)

@Composable
fun RayBan_00Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
