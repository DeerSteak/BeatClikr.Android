package com.bfunkstudios.beatclikr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.data.PlaylistEntryWithSong
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.ui.components.PlaylistTransportView
import com.bfunkstudios.beatclikr.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailView(
    viewModel: PlaylistViewModel,
    editMode: Boolean,
    showSongPicker: Boolean,
    onSongPickerDismiss: () -> Unit,
    isPlaying: Boolean,
    beatPulse: Float,
    onPlayPause: () -> Unit,
    onPlaySong: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val playlist by viewModel.selectedPlaylist.collectAsState()
    val entries = viewModel.sortedEntries(playlist)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = stringResource(R.string.empty_playlist_detail),
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                LazyColumn {
                    items(entries, key = { it.entry.id }) { entryWithSong ->
                        PlaylistEntryRow(
                            entry = entryWithSong,
                            isCurrent = viewModel.currentEntryId == entryWithSong.entry.id,
                            editMode = editMode,
                            onClick = { viewModel.playSong(entryWithSong, onPlaySong) },
                            onDelete = { viewModel.deleteEntry(entryWithSong, entries) }
                        )
                        if (entryWithSong != entries.last()) {
                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }

            if (!editMode) {
                PlaylistTransportView(
                    currentTitle = viewModel.currentSongTitle(entries),
                    isPlaying = isPlaying,
                    beatPulse = beatPulse,
                    canGoPrevious = viewModel.canGoPrevious(entries),
                    canGoNext = viewModel.canGoNext(entries),
                    onPlayPause = onPlayPause,
                    onPlay = { viewModel.playOrResume(entries, onPlaySong) },
                    onPrevious = { viewModel.playPrevious(entries, onPlaySong) },
                    onNext = { viewModel.playNext(entries, onPlaySong) }
                )
            }
        }
    }

    if (showSongPicker) {
        val songs by viewModel.allSongs.collectAsState()
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = onSongPickerDismiss,
            sheetState = sheetState
        ) {
            Text(
                text = stringResource(R.string.add_song),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn {
                items(songs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        editMode = false,
                        isCurrent = false,
                        onClick = {
                            viewModel.addSong(song.id)
                            onSongPickerDismiss()
                        },
                        onDelete = {},
                        onEdit = {}
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun PlaylistEntryRow(
    entry: PlaylistEntryWithSong,
    isCurrent: Boolean,
    editMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .clickable(enabled = !editMode, onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(16.dp)
                .alpha(if (isCurrent) 1f else 0f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = entry.song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (editMode) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
