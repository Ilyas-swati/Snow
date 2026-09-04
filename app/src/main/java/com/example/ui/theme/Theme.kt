package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF020B14),
    primaryContainer = Color(0xFF003640),
    onPrimaryContainer = IceFrost,
    secondary = IceFrost,
    onSecondary = Color(0xFF003640),
    secondaryContainer = Color(0xFF0A2234),
    onSecondaryContainer = Color(0xFFB0ECFD),
    tertiary = NeonViolet,
    background = DarkSpace,
    onBackground = Color(0xFFF1F5F9),
    surface = CosmicNavy,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = CardNavy,
    onSurfaceVariant = Color(0xFF94A3B8)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
