package com.bfunkstudios.beatclikr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.ui.SongLibraryUiState
import com.bfunkstudios.beatclikr.ui.SongLibraryViewModel

@Composable
fun SongLibraryView(
    uiState: SongLibraryUiState,
    viewModel: SongLibraryViewModel,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    isPlaying: Boolean = false,
    beatPulse: Float = 0f,
    onPlayPause: () -> Unit = {},
    onPlaySong: (Song) -> Unit = {},
    navigateToDetail: () -> Unit = {}
) {
    val songs = uiState.songList

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = stringResource(R.string.empty_song_library),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                LazyColumn {
                    items(songs, key = { it.id }) { song ->
                        SongListItem(
                            song = song,
                            editMode = editMode,
                            isCurrent = viewModel.currentSongId == song.id,
                            onClick = {
                                viewModel.markSongPlaying(song)
                                onPlaySong(song)
                            },
                            onDelete = { viewModel.deleteSong(song) },
                            onEdit = {
                                viewModel.setSelectedSong(song.id)
                                navigateToDetail()
                            }
                        )
                        if (song != songs.last()) {
                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }

            if (!editMode) {
                PlaylistTransportView(
                    currentTitle = viewModel.currentSongTitle(songs),
                    isPlaying = isPlaying,
                    beatPulse = beatPulse,
                    canGoPrevious = viewModel.canGoPrevious(songs),
                    canGoNext = viewModel.canGoNext(songs),
                    onPlayPause = onPlayPause,
                    onPlay = { viewModel.playOrResume(songs, onPlaySong) },
                    onPrevious = { viewModel.playPrevious(songs, onPlaySong) },
                    onNext = { viewModel.playNext(songs, onPlaySong) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistTransportView(
    currentTitle: String?,
    isPlaying: Boolean,
    beatPulse: Float,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val pulseAlpha = if (isPlaying) beatPulse.coerceIn(0f, 1f) * 0.35f else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportTextButton(
                text = stringResource(R.string.previous),
                iconLeading = true,
                enabled = canGoPrevious,
                onClick = onPrevious,
                modifier = Modifier.weight(1f)
            )
            PlayPauseTransportButton(
                isPlaying = isPlaying,
                onClick = {
                    if (isPlaying) onPlayPause() else onPlay()
                }
            )
            TransportTextButton(
                text = stringResource(R.string.next),
                iconLeading = false,
                enabled = canGoNext,
                onClick = onNext,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = currentTitle?.let { stringResource(R.string.now_playing) }
                ?: stringResource(R.string.tap_song_to_begin),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = currentTitle ?: "--",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun TransportTextButton(
    text: String,
    iconLeading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    ) {
        if (iconLeading) {
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text = text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        if (!iconLeading) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun PlayPauseTransportButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.medium)
    ) {
        if (isPlaying) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 6.dp, height = 24.dp)
                        .background(MaterialTheme.colorScheme.onSecondary)
                )
                Box(
                    modifier = Modifier
                        .size(width = 6.dp, height = 24.dp)
                        .background(MaterialTheme.colorScheme.onSecondary)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.play),
                tint = MaterialTheme.colorScheme.onSecondary
            )
        }
    }
}
