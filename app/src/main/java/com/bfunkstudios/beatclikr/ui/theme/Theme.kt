package com.bfunkstudios.beatclikr.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AppPrimaryDark,           // Blue for dark mode
    onPrimary = Color.White,
    secondary = AccentColor,            // Orange accent
    onSecondary = Color.White,          // White on orange, both modes
    tertiary = AccentColor,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = AppPrimaryLight,          // Blue for light mode
    onPrimary = Color.White,
    secondary = AccentColor,            // Orange accent
    onSecondary = Color.White,          // White on orange, both modes
    tertiary = AccentColor,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight
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
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}