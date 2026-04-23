package com.bfunkstudios.beatclikr.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.ui.components.BpmSliderControl
import com.bfunkstudios.beatclikr.ui.components.GrooveSelector
import com.bfunkstudios.beatclikr.ui.components.MetronomePlayerView
import com.bfunkstudios.beatclikr.ui.components.SectionCard
import com.bfunkstudios.beatclikr.ui.components.SoundPickerRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstantMetronomeView(
    modifier: Modifier = Modifier,
    viewModel: MetronomeViewModel = hiltViewModel()
) {
    DisposableEffect(Unit) {
        // Setup metronome when view appears with default sounds
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
            // BPM Card
            SectionCard {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Top row: Player, BPM, Tap Tempo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Player circle with fixed spacer
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            // Invisible spacer to maintain fixed size (like iOS Color.clear)
                            Spacer(modifier = Modifier.size(MetronomeConstants.PLAYER_VIEW_DEFAULT_SIZE.dp))

                            // Animated player view
                            MetronomePlayerView(
                                scale = viewModel.iconScale,
                                bpm = viewModel.beatsPerMinute,
                                size = MetronomeConstants.PLAYER_VIEW_DEFAULT_SIZE.dp
                            )
                        }

                        // BPM Display
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

                        // Tap Tempo Button
                        Box(
                            modifier = Modifier
                                .size(MetronomeConstants.PLAYER_VIEW_DEFAULT_SIZE.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
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
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    BpmSliderControl(
                        value = viewModel.beatsPerMinute,
                        onValueChange = { viewModel.updateBPM(it) },
                        valueRange = MetronomeConstants.MIN_BPM..MetronomeConstants.MAX_BPM
                    )
                }
            }

            // Groove Card
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
                        selected = viewModel.selectedSubdivisions,
                        onSelect = { viewModel.updateSubdivisions(it) }
                    )
                }
            }

            // Beat & Rhythm Card
            SectionCard {
                Column {
                    SoundPickerRow(
                        label = stringResource(R.string.beat),
                        selected = viewModel.selectedBeatSound,
                        options = SoundFile.beatSounds,
                        onSelect = { viewModel.updateBeatSound(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 12.dp))
                    SoundPickerRow(
                        label = stringResource(R.string.rhythm),
                        selected = viewModel.selectedRhythmSound,
                        options = SoundFile.rhythmSounds,
                        onSelect = { viewModel.updateRhythmSound(it) }
                    )
                }
            }

            // Play/Pause Button
            Button(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary // Orange accent
                )
            ) {
                if (viewModel.isPlaying) {
                    // Pause icon (two vertical bars)
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
                                        MaterialTheme.colorScheme.onSecondary,
                                        RoundedCornerShape(1.dp)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        MaterialTheme.colorScheme.onSecondary,
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
                    text = if (viewModel.isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

