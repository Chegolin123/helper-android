package com.helper.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Фирменная палитра «Sasha Violet» (Google Stitch, дизайн-система Helper).
private val DarkColors = darkColorScheme(
    primary = Violet40,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = BubbleUser,
    onPrimaryContainer = Violet90,
    secondary = Teal40,
    onSecondary = Color(0xFF00201A),
    secondaryContainer = Color(0xFF004D42),
    onSecondaryContainer = Teal80,
    background = DarkBackground,
    onBackground = Color(0xFFE6E1F5),
    surface = DarkSurface,
    onSurface = Color(0xFFE6E1F5),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB8B2CC),
    outline = Color(0xFF8A849E),
)

private val LightColors = lightColorScheme(
    primary = Violet40,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Violet90,
    onPrimaryContainer = Color(0xFF1A1721),
    secondary = Teal40,
    background = LightBackground,
    onBackground = Color(0xFF1A1721),
    surface = LightSurface,
    onSurface = Color(0xFF1A1721),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4A4554),
)

/**
 * Тема Helper. Фирменная палитра «Sasha Violet» всегда (без Dynamic Color),
 * чтобы приложение совпадало с дизайном Google Stitch.
 */
@Composable
fun HelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HelperTypography,
        content = content,
    )
}