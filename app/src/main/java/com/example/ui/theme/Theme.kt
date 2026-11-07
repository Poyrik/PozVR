package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VrColorScheme = darkColorScheme(
    primary = VrCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF80F2FF),
    secondary = VrAmber,
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF6B3C00),
    onSecondaryContainer = Color(0xFFFFDCBE),
    tertiary = VrGreen,
    onTertiary = Color(0xFF003915),
    background = VrBackground,
    onBackground = VrOnBackground,
    surface = VrSurface,
    onSurface = VrOnSurface,
    surfaceVariant = VrSurfaceVariant,
    onSurfaceVariant = VrOnSurfaceVariant,
    outline = VrOutline
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VrColorScheme,
        typography = Typography,
        content = content
    )
}
