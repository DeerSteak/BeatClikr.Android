package com.bfunkstudios.beatclikr.ui

import com.bfunkstudios.beatclikr.ui.theme.AccentColor
import com.bfunkstudios.beatclikr.ui.theme.AccentColorDark
import com.bfunkstudios.beatclikr.ui.theme.AppPrimaryDark
import com.bfunkstudios.beatclikr.ui.theme.AppPrimaryLight
import com.bfunkstudios.beatclikr.ui.theme.SurfaceDark
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun `brand controls meet normal-text contrast`() {
        assertTrue(contrast(AccentColor, Color.White) >= 4.5)
        assertTrue(contrast(AccentColorDark, Color.Black) >= 4.5)
        assertTrue(contrast(AppPrimaryLight, Color.White) >= 4.5)
        assertTrue(contrast(AppPrimaryDark, Color.Black) >= 4.5)
        assertTrue(contrast(AppPrimaryDark, SurfaceDark) >= 4.5)
    }

    private fun contrast(first: Color, second: Color): Double {
        val bright = maxOf(luminance(first), luminance(second))
        val dark = minOf(luminance(first), luminance(second))
        return (bright + 0.05) / (dark + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
