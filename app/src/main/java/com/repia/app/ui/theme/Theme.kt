package com.repia.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonEmerald,
    onPrimary = Color.Black,
    primaryContainer = SurfaceDark,
    onPrimaryContainer = NeonEmerald,
    secondary = NeonCyan,
    onSecondary = Color.Black,
    tertiary = NeonPurple,
    error = NeonRed,
    background = BackgroundDark,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder
)

private val LightColorScheme = lightColorScheme(
    primary = NeonEmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8F0),
    onPrimaryContainer = NeonEmeraldDark,
    secondary = NeonCyan,
    background = Color(0xFFF1F5F9),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF1E293B),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun RepIATheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = RepIAShapes,
        typography = RepIATypography,
        content = content
    )
}
