package com.bfunkstudios.beatclikr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bfunkstudios.beatclikr.data.Song

@Composable
fun PlaylistFocusView(
    viewModel: PlaylistViewModel,
    isPlaying: Boolean,
    beatPulse: Float,
    onPlayPause: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val playlist by viewModel.selectedPlaylist.collectAsState()
        val entries = viewModel.sortedEntries(playlist)
        val currentTitle = viewModel.currentSongTitle(entries)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val circleSize = minOf(maxWidth, maxHeight) * 0.8f

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Close button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, end = 20.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    PulsingCircles(circleSize = circleSize, beatPulse = beatPulse)

                    Spacer(modifier = Modifier.weight(1f))

                    // Now Playing label
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 28.dp)
                    ) {
                        if (currentTitle != null) {
                            Text(
                                text = "Now Playing",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentTitle,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }

                    // Transport
                    FocusTransportRow(
                        canGoPrevious = viewModel.canGoPrevious(entries),
                        canGoNext = viewModel.canGoNext(entries),
                        isPlaying = isPlaying,
                        onPrevious = { viewModel.playPrevious(entries, onPlaySong) },
                        onNext = { viewModel.playNext(entries, onPlaySong) },
                        onPlayPause = {
                            if (isPlaying) onPlayPause()
                            else viewModel.playOrResume(entries, onPlaySong)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 40.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PulsingCircles(circleSize: Dp, beatPulse: Float) {
    Box(
        modifier = Modifier.size(circleSize * 1.25f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(circleSize * 1.25f)
                .graphicsLayer {
                    val scale = 0.75f + beatPulse * 0.25f
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = (beatPulse * 0.1f).coerceIn(0f, 1f)))
        )
        Box(
            modifier = Modifier
                .size(circleSize)
                .graphicsLayer {
                    val scale = 0.7f + beatPulse * 0.3f
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = (0.08f + beatPulse * 0.62f).coerceIn(0f, 1f)))
        )
    }
}

@Composable
private fun FocusTransportRow(
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FocusNavButton(
            label = "Previous",
            iconLeading = true,
            enabled = canGoPrevious,
            onClick = onPrevious,
            modifier = Modifier.weight(1f)
        )

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        FocusNavButton(
            label = "Next",
            iconLeading = false,
            enabled = canGoNext,
            onClick = onNext,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FocusNavButton(
    label: String,
    iconLeading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgAlpha = if (enabled) 0.18f else 0.06f
    val contentAlpha = if (enabled) 1f else 0.35f

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = bgAlpha)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (iconLeading) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = contentAlpha),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = label,
                    color = Color.White.copy(alpha = contentAlpha),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                if (!iconLeading) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = contentAlpha),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
