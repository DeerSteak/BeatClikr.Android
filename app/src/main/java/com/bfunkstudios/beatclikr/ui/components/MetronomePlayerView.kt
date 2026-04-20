package com.bfunkstudios.beatclikr.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bfunkstudios.beatclikr.constants.MetronomeConstants

@Composable
fun MetronomePlayerView(
    scale: Float,
    bpm: Float,
    size: Dp = MetronomeConstants.PLAYER_VIEW_DEFAULT_SIZE.dp,
    modifier: Modifier = Modifier
) {
    val animatedScale = remember { Animatable(scale) }
    val beatDurationMs = (60_000.0 / bpm).toInt()

    LaunchedEffect(scale) {
        if (scale == MetronomeConstants.ICON_SCALE_MAX) {
            // Instant jump to max (no animation)
            animatedScale.snapTo(MetronomeConstants.ICON_SCALE_MAX)
        } else {
            // Smooth animation down to min over beat duration
            animatedScale.animateTo(
                targetValue = scale,
                animationSpec = tween(
                    durationMillis = beatDurationMs,
                    easing = LinearEasing
                )
            )
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * animatedScale.value)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
        )
    }
}
