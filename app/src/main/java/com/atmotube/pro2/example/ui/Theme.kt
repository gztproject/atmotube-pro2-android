package com.atmotube.pro2.example.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.atmotube.pro2.example.AirLevel

private val Teal = Color(0xFF0F6E6E)
private val TealDark = Color(0xFF7DCEC8)
private val Ink = Color(0xFF1C1B19)
private val Paper = Color(0xFFF4F1EC)
private val CardLight = Color(0xFFFFFCF8)
private val Muted = Color(0xFF5E594F)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Color(0xFF3D6B4F),
    background = Paper,
    surface = CardLight,
    surfaceVariant = Color(0xFFE7E2D9),
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = Muted,
    outline = Color(0xFFD3CDC2),
)

private val DarkColors = darkColorScheme(
    primary = TealDark,
    onPrimary = Color(0xFF003230),
    secondary = Color(0xFF9ED4B4),
    background = Color(0xFF121416),
    surface = Color(0xFF1C1F22),
    surfaceVariant = Color(0xFF2A2E32),
    onBackground = Color(0xFFE8E6E3),
    onSurface = Color(0xFFE8E6E3),
    onSurfaceVariant = Color(0xFFB7B2A8),
    outline = Color(0xFF3A3F44),
)

@Composable
fun AtmotubeAirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

/** Official EAQI swatches, used for the category bar. */
@Composable
fun AirLevel.band(): Color = when (this) {
    AirLevel.GOOD -> Color(0xFF50F0E6)
    AirLevel.FAIR -> Color(0xFF50CCAA)
    AirLevel.MODERATE -> Color(0xFFF0E641)
    AirLevel.POOR -> Color(0xFFFF5050)
    AirLevel.VERY_POOR -> Color(0xFF960032)
    AirLevel.EXTREMELY_POOR -> Color(0xFF7D2181)
    AirLevel.UNKNOWN -> if (isSystemInDarkTheme()) Color(0xFFB7B2A8) else Color(0xFF8A847A)
}

/** Darker text colors so the EAQI name stays readable on a light card. */
@Composable
fun AirLevel.indicator(): Color {
    val dark = isSystemInDarkTheme()
    return when (this) {
        AirLevel.GOOD -> if (dark) Color(0xFF50F0E6) else Color(0xFF0B6E66)
        AirLevel.FAIR -> if (dark) Color(0xFF50CCAA) else Color(0xFF0E6B52)
        AirLevel.MODERATE -> if (dark) Color(0xFFF0E641) else Color(0xFF8A6A00)
        AirLevel.POOR -> if (dark) Color(0xFFFF8A80) else Color(0xFFC4382D)
        AirLevel.VERY_POOR -> if (dark) Color(0xFFFF8A9A) else Color(0xFF960032)
        AirLevel.EXTREMELY_POOR -> if (dark) Color(0xFFE08BE4) else Color(0xFF7D2181)
        AirLevel.UNKNOWN -> if (dark) Color(0xFFB7B2A8) else Color(0xFF8A847A)
    }
}
