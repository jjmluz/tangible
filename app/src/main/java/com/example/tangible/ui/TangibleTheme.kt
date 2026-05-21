package com.example.tangible.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TangiblePurple = Color(0xFF9B59F6)
val TangiblePurpleDark = Color(0xFF6D28D9)
val TangibleBackground = Color(0xFF0E0E14)
val TangibleSurface = Color(0xFF1A1A26)
val TangibleSurfaceVariant = Color(0xFF242436)

private val colorScheme = darkColorScheme(
    primary = TangiblePurple,
    onPrimary = Color.White,
    primaryContainer = TangiblePurpleDark,
    onPrimaryContainer = Color.White,
    secondary = TangiblePurple,
    onSecondary = Color.White,
    secondaryContainer = TangiblePurpleDark,
    onSecondaryContainer = Color.White,
    background = TangibleBackground,
    onBackground = Color.White,
    surface = TangibleSurface,
    onSurface = Color.White,
    surfaceVariant = TangibleSurfaceVariant,
    onSurfaceVariant = Color(0xFFB0B0CC),
    outline = Color(0xFF3A3A52),
    error = Color(0xFFCF6679),
    onError = Color.White,
    inverseSurface = Color(0xFFE8E8F4),
    inverseOnSurface = TangibleSurface,
)

@Composable
fun TangibleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
