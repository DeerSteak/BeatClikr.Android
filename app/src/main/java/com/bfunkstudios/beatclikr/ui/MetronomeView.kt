package com.bfunkstudios.beatclikr.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.constants.AppLocale
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.ui.components.BeatPatternSelector
import com.bfunkstudios.beatclikr.ui.components.BpmSliderControl
import com.bfunkstudios.beatclikr.ui.components.GrooveSelector
import com.bfunkstudios.beatclikr.ui.components.MetronomePlayerView
import com.bfunkstudios.beatclikr.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeView(
    modifier: Modifier = Modifier,
    viewModel: MetronomeViewModel = hiltViewModel()
) {
    DisposableEffect(Unit) {
        val beatResId = viewModel.selectedBeatSound.resourceId
        val rhythmResId = viewModel.selectedRhythmSound.resourceId
        if (beatResId != null && rhythmResId != null) {
            viewModel.setupMetronome(beatResId, rhythmResId)
        }

        onDispose {
            viewModel.stop()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                    Box(contentAlignment = Alignment.Center) {
                        Spacer(modifier = Modifier.size(MetronomeConstants.PLAYER_VIEW_DEFAULT_SIZE.dp))
                        MetronomePlayerView(
                            scale = viewModel.iconScale,
                            bpm = viewModel.beatsPerMinute,
                            size = MetronomeConstants.PLAYER_VIEW_DEFAULT_SIZE.dp
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val animatedBpm by animateFloatAsState(
                            targetValue = viewModel.beatsPerMinute,
                            label = "bpm_animation"
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

                    Box(
                        modifier = Modifier
                            .size(MetronomeConstants.PLAYER_VIEW_DEFAULT_SIZE.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                            .clickable { viewModel.recordTap() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.tap_tempo),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                BpmSliderControl(
                    value = viewModel.beatsPerMinute,
                    onValueChange = { viewModel.updateBPM(it) },
                    valueRange = MetronomeConstants.MIN_BPM..MetronomeConstants.MAX_BPM,
                    enabled = !(viewModel.rampEnabled && viewModel.isPlaying)
                )
            }
        }

        SectionCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.groove),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                GrooveSelector(
                    selected = viewModel.selectedGroove,
                    onSelect = { viewModel.updateGroove(it) }
                )
                if (viewModel.selectedGroove.isOddMeter) {
                    Text(
                        text = stringResource(R.string.beat_pattern),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp)
                    )
                    BeatPatternSelector(
                        selected = viewModel.selectedBeatPattern,
                        onSelect = { viewModel.updateBeatPattern(it) }
                    )
                }
            }
        }

        SectionCard {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tempo_ramp),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = viewModel.rampEnabled,
                        onCheckedChange = { viewModel.updateRampEnabled(it) }
                    )
                }

                if (viewModel.rampEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(start = 12.dp))
                    RampStepperRow(
                        label = stringResource(R.string.ramp_increase_by),
                        valueLabel = stringResource(R.string.ramp_bpm_value, viewModel.rampIncrement),
                        onDecrease = {
                            val options = listOf(1, 2, 5, 10)
                            viewModel.updateRampIncrement(previousOption(viewModel.rampIncrement, options))
                        },
                        onIncrease = {
                            val options = listOf(1, 2, 5, 10)
                            viewModel.updateRampIncrement(nextOption(viewModel.rampIncrement, options))
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 12.dp))
                    RampStepperRow(
                        label = stringResource(R.string.ramp_every),
                        valueLabel = stringResource(R.string.ramp_beats_value, viewModel.rampInterval),
                        onDecrease = {
                            val options = listOf(4, 8, 16, 32, 48, 64)
                            viewModel.updateRampInterval(previousOption(viewModel.rampInterval, options))
                        },
                        onIncrease = {
                            val options = listOf(4, 8, 16, 32, 48, 64)
                            viewModel.updateRampInterval(nextOption(viewModel.rampInterval, options))
                        }
                    )
                }
            }
        }

        PlayPauseButton(
            isPlaying = viewModel.isPlaying,
            onClick = { viewModel.togglePlayPause() }
        )
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .padding(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                MaterialTheme.colorScheme.onPrimary,
                                RoundedCornerShape(1.dp)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                MaterialTheme.colorScheme.onPrimary,
                                RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        } else {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun RampStepperRow(
    label: String,
    valueLabel: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDecrease) {
            Icon(imageVector = Icons.Default.Remove, contentDescription = null)
        }
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp)
        )
        IconButton(onClick = onIncrease) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        }
    }
}

private fun previousOption(current: Int, options: List<Int>): Int {
    val currentIndex = options.indexOf(current).takeIf { it >= 0 } ?: 0
    return options[(currentIndex - 1).coerceAtLeast(0)]
}

private fun nextOption(current: Int, options: List<Int>): Int {
    val currentIndex = options.indexOf(current).takeIf { it >= 0 } ?: 0
    return options[(currentIndex + 1).coerceAtMost(options.lastIndex)]
}
