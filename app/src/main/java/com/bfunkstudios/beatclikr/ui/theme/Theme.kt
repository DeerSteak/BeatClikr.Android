package com.bfunkstudios.beatclikr.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AppPrimaryDark,           // Blue for dark mode
    secondary = AccentColor,            // Orange accent
    tertiary = AccentColor,             // Use accent instead of purple
    surface = SurfaceDark,              // Dark surface
    surfaceVariant = SurfaceVariantDark // Card container dark
)

private val LightColorScheme = lightColorScheme(
    primary = AppPrimaryLight,          // Blue for light mode
    secondary = AccentColor,            // Orange accent
    tertiary = AccentColor,             // Use accent instead of purple
    surface = SurfaceLight,             // Pure white
    surfaceVariant = SurfaceVariantLight // iOS gray background
)

@Composable
fun BeatClikrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled to use custom BeatClikr brand colors
    dynamicColor: Boolean = false,
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}