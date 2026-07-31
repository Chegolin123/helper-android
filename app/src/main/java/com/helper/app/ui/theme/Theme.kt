package com.helper.app.ui.theme

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

private val DarkColors = darkColorScheme(
    primary = Violet80,
    onPrimary = Color(0xFF000000),
    primaryContainer = Violet40,
    onPrimaryContainer = Violet90,
    secondary = Teal80,
    background = DarkBackground,
    onBackground = Color(0xFFF5F2FA),
    surface = DarkSurface,
    onSurface = Color(0xFFF5F2FA),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB8B2C4),
)

private val LightColors = lightColorScheme(
    primary = Violet40,
    onPrimary = Color(0xFFF5F2FA),
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

/** Тема приложения. На Android 12+ подхватывает Dynamic Color, иначе — брендинг Саши. */
@Composable
fun HelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = HelperTypography,
        content = content,
    )
}
