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
    primary = AppPrimaryDark,           // Light blue for dark mode (readable on dark surfaces)
    onPrimary = Color.White,      // Dark navy — contrast on the light blue primary
    primaryContainer = AppPrimaryContainerDark,
    onPrimaryContainer = Color.White,
    secondary = AccentColor,            // Orange accent
    onSecondary = Color.White,          // White on orange, both modes
    secondaryContainer = AppPrimaryContainerDark,
    onSecondaryContainer = Color.White,
    tertiary = AccentColor,
    tertiaryContainer = AccentContainerDark,
    onTertiaryContainer = Color.White,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = AppPrimaryLight,          // Blue for light mode
    onPrimary = Color.White,
    primaryContainer = AppPrimaryContainerLight,
    onPrimaryContainer = AppPrimaryDark,
    secondary = AccentColor,            // Orange accent
    onSecondary = Color.White,          // White on orange, both modes
    secondaryContainer = AppPrimaryContainerLight,
    onSecondaryContainer = AppPrimaryDark,
    tertiary = AccentColor,
    tertiaryContainer = AccentContainerLight,
    onTertiaryContainer = Color(0xFF8F2F14),
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight
)

@Composable
fun BeatClikrTheme(
    forceDarkTheme: Boolean = false,
    darkTheme: Boolean = forceDarkTheme || isSystemInDarkTheme(),
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
