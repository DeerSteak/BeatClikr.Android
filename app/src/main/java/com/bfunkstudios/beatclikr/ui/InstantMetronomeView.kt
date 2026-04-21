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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.data.Subdivisions
import com.bfunkstudios.beatclikr.ui.components.MetronomePlayerView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstantMetronomeView(
    viewModel: MetronomeViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    var showBeatMenu by remember { mutableStateOf(false) }
    var showRhythmMenu by remember { mutableStateOf(false) }

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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
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
                                text = String.format("%.0f", animatedBpm),
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

                    // BPM Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minus Button
                        OutlinedIconButton(
                            onClick = {
                                viewModel.updateBPM(
                                    (viewModel.beatsPerMinute - 1f)
                                        .coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM)
                                )
                            },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.outlinedIconButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(
                                text = "−",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Slider
                        Slider(
                            value = viewModel.beatsPerMinute,
                            onValueChange = { viewModel.updateBPM(it) },
                            valueRange = MetronomeConstants.MIN_BPM..MetronomeConstants.MAX_BPM,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary,
                                inactiveTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
                            )
                        )

                        // Plus Button
                        OutlinedIconButton(
                            onClick = {
                                viewModel.updateBPM(
                                    (viewModel.beatsPerMinute + 1f)
                                        .coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM)
                                )
                            },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.outlinedIconButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.increase_bpm)
                            )
                        }
                    }
                }
            }

            // Groove Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
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

                    // 2x2 Grid
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Subdivisions.entries.take(2).forEach { subdivision ->
                                GrooveButton(
                                    subdivision = subdivision,
                                    isSelected = viewModel.selectedSubdivisions == subdivision,
                                    onClick = { viewModel.updateSubdivisions(subdivision) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Subdivisions.entries.drop(2).forEach { subdivision ->
                                GrooveButton(
                                    subdivision = subdivision,
                                    isSelected = viewModel.selectedSubdivisions == subdivision,
                                    onClick = { viewModel.updateSubdivisions(subdivision) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Beat & Rhythm Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    // Beat Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.beat),
                            style = MaterialTheme.typography.titleMedium
                        )

                        Box {
                            TextButton(onClick = { showBeatMenu = true }) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = viewModel.selectedBeatSound.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showBeatMenu,
                                onDismissRequest = { showBeatMenu = false }
                            ) {
                                SoundFile.beatSounds.forEach { sound ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                sound.displayName,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        },
                                        onClick = {
                                            viewModel.updateBeatSound(sound)
                                            showBeatMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(start = 12.dp))

                    // Rhythm Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.rhythm),
                            style = MaterialTheme.typography.titleMedium
                        )

                        Box {
                            TextButton(onClick = { showRhythmMenu = true }) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = viewModel.selectedRhythmSound.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showRhythmMenu,
                                onDismissRequest = { showRhythmMenu = false }
                            ) {
                                SoundFile.rhythmSounds.forEach { sound ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                sound.displayName,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        },
                                        onClick = {
                                            viewModel.updateRhythmSound(sound)
                                            showRhythmMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
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
                                        MaterialTheme.colorScheme.onPrimary,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        MaterialTheme.colorScheme.onPrimary,
                                        RoundedCornerShape(2.dp)
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

@Composable
private fun GrooveButton(
    subdivision: Subdivisions,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondary // Orange accent for selected
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onSecondary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Text(
            text = when (subdivision) {
                Subdivisions.Quarter -> stringResource(R.string.subdivision_quarter)
                Subdivisions.Eighth -> stringResource(R.string.subdivision_eighth)
                Subdivisions.Triplet -> stringResource(R.string.subdivision_triplet)
                Subdivisions.Sixteenth -> stringResource(R.string.subdivision_sixteenth)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
