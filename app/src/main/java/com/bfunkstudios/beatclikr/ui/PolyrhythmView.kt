package com.bfunkstudios.beatclikr.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.constants.AppLocale
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.ui.components.BpmSliderControl
import com.bfunkstudios.beatclikr.ui.components.SectionCard

@Composable
fun PolyrhythmView(
    modifier: Modifier = Modifier,
    viewModel: PolyrhythmViewModel = hiltViewModel()
) {
    DisposableEffect(Unit) {
        viewModel.setupPolyrhythm()
        onDispose { viewModel.stop() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CountSelector(
                        label = stringResource(R.string.rhythm),
                        value = viewModel.beats,
                        onDecrease = { viewModel.updateBeats(viewModel.beats - 1) },
                        onIncrease = { viewModel.updateBeats(viewModel.beats + 1) },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = ":",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Thin,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(32.dp)
                    )
                    CountSelector(
                        label = stringResource(R.string.beat),
                        value = viewModel.against,
                        onDecrease = { viewModel.updateAgainst(viewModel.against - 1) },
                        onIncrease = { viewModel.updateAgainst(viewModel.against + 1) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PolyrhythmDotRow(
                        label = stringResource(R.string.beat),
                        count = viewModel.against,
                        activeIndex = viewModel.activeBeatIndex,
                        pulse = viewModel.beatPulse,
                        color = MaterialTheme.colorScheme.primary
                    )
                    PolyrhythmDotRow(
                        label = stringResource(R.string.rhythm),
                        count = viewModel.beats,
                        activeIndex = viewModel.activeRhythmIndex,
                        pulse = viewModel.rhythmPulse,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    PolyrhythmPlayheadRow(
                        isPlaying = viewModel.isPlaying,
                        resetId = viewModel.playheadResetID,
                        cycleDurationMillis = viewModel.cycleDurationMillis
                    )
                }
            }
        }

        SectionCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val animatedBpm by animateFloatAsState(
                            targetValue = viewModel.bpm,
                            label = "polyrhythm_bpm_animation"
                        )
                        Text(
                            text = String.format(AppLocale, "%.0f", animatedBpm),
                            fontSize = 60.sp,
                            fontWeight = FontWeight.Thin,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.bpm),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp
                        )
                    }
                }
                BpmSliderControl(
                    value = viewModel.bpm,
                    onValueChange = { viewModel.updateBpm(it) },
                    valueRange = MetronomeConstants.MIN_BPM..MetronomeConstants.MAX_BPM
                )
            }
        }

        Button(
            onClick = { viewModel.togglePlayPause() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            if (viewModel.isPlaying) {
                PauseGlyph()
            } else {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (viewModel.isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun CountSelector(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            maxLines = 1
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDecrease, enabled = value > 1) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = null)
            }
            Text(
                text = value.toString(),
                fontSize = 40.sp,
                fontWeight = FontWeight.Thin,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(44.dp)
            )
            IconButton(onClick = onIncrease, enabled = value < 15) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
            }
        }
    }
}

@Composable
private fun PolyrhythmDotRow(
    label: String,
    count: Int,
    activeIndex: Int,
    pulse: Float,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.width(68.dp)
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
        ) {
            val centerY = size.height / 2
            drawLine(
                color = color.copy(alpha = 0.2f),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            val dotRadius = 9.dp.toPx()
            repeat(count) { index ->
                val x = size.width * index / count.coerceAtLeast(1)
                val isActive = index == activeIndex
                val alpha = if (isActive) 0.25f + 0.75f * pulse else 0.25f
                val scale = if (isActive) 0.85f + 0.15f * pulse else 0.85f
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = dotRadius * scale,
                    center = Offset(x.coerceIn(dotRadius, size.width - dotRadius), centerY)
                )
            }
        }
    }
}

@Composable
private fun PolyrhythmPlayheadRow(
    isPlaying: Boolean,
    resetId: Int,
    cycleDurationMillis: Int
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(resetId, isPlaying, cycleDurationMillis) {
        progress.snapTo(0f)
        if (isPlaying) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = cycleDurationMillis, easing = LinearEasing)
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(modifier = Modifier.width(68.dp))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
        ) {
            val centerY = size.height / 2
            val dotRadius = 7.dp.toPx()
            drawLine(
                color = Color(0xFFFF9800).copy(alpha = 0.25f),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            val x = dotRadius + (size.width - 2 * dotRadius) * progress.value
            drawOval(
                color = Color(0xFFFF9800),
                topLeft = Offset(x - dotRadius, centerY - dotRadius),
                size = Size(dotRadius * 2, dotRadius * 2)
            )
        }
    }
}

@Composable
private fun PauseGlyph() {
    Box(
        modifier = Modifier
            .size(24.dp)
            .padding(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.onSecondary, RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.onSecondary, RoundedCornerShape(1.dp))
            )
        }
    }
}
