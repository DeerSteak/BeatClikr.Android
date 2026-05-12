package com.bfunkstudios.beatclikr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.data.PlaylistEntryWithSong
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.ui.components.PlaylistTransportView
import com.bfunkstudios.beatclikr.ui.components.SongListItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    onEditSong: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val playlist by viewModel.selectedPlaylist.collectAsState()
    val entries = viewModel.sortedEntries(playlist)

    val localEntries = remember { mutableStateListOf<PlaylistEntryWithSong>() }
    LaunchedEffect(entries) {
        localEntries.clear()
        localEntries.addAll(entries)
    }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localEntries.add(to.index, localEntries.removeAt(from.index))
    }

    val currentEntryId = viewModel.currentEntryId
    LaunchedEffect(currentEntryId) {
        if (currentEntryId != null) {
            val index = localEntries.indexOfFirst { it.entry.id == currentEntryId }
            if (index >= 0) lazyListState.animateScrollToItem(index)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (localEntries.isEmpty()) {
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
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                LazyColumn(state = lazyListState) {
                    itemsIndexed(localEntries, key = { _, it -> it.entry.id }) { index, entryWithSong ->
                        ReorderableItem(reorderState, key = entryWithSong.entry.id) { isDragging ->
                            val isCurrent = viewModel.currentEntryId == entryWithSong.entry.id
                            SongListItem(
                                song = entryWithSong.song,
                                onClick = { viewModel.playSong(entryWithSong, onPlaySong) },
                                modifier = Modifier.background(
                                    when {
                                        isDragging -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        else -> Color.Transparent
                                    }
                                ),
                                editMode = editMode,
                                isCurrent = isCurrent,
                                onDelete = { viewModel.deleteEntry(entryWithSong, localEntries.toList()) },
                                onEdit = { onEditSong(entryWithSong.song) },
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStopped = { viewModel.reorderEntries(localEntries.toList()) }
                                )
                            )
                        }
                        if (index < localEntries.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }

            if (!editMode) {
                PlaylistTransportView(
                    currentTitle = viewModel.currentSongTitle(localEntries.toList()),
                    isPlaying = isPlaying,
                    beatPulse = beatPulse,
                    canGoPrevious = viewModel.canGoPrevious(localEntries.toList()),
                    canGoNext = viewModel.canGoNext(localEntries.toList()),
                    onPlayPause = onPlayPause,
                    onPlay = { viewModel.playOrResume(localEntries.toList(), onPlaySong) },
                    onPrevious = { viewModel.playPrevious(localEntries.toList(), onPlaySong) },
                    onNext = { viewModel.playNext(localEntries.toList(), onPlaySong) }
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
