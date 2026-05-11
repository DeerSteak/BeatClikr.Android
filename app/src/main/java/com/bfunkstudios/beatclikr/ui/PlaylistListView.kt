package com.bfunkstudios.beatclikr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.data.PlaylistWithEntries
import java.util.UUID

@Composable
fun PlaylistListView(
    viewModel: PlaylistViewModel,
    editMode: Boolean,
    showNewPlaylistDialog: Boolean,
    onNewPlaylistDialogDismiss: () -> Unit,
    onNavigateToDetail: (UUID) -> Unit,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.playlists.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = stringResource(R.string.empty_playlist_list),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                LazyColumn {
                    items(playlists, key = { it.playlist.id }) { playlistWithEntries ->
                        PlaylistRow(
                            playlistWithEntries = playlistWithEntries,
                            editMode = editMode,
                            onClick = { onNavigateToDetail(playlistWithEntries.playlist.id) },
                            onRename = { viewModel.beginRenamePlaylist(playlistWithEntries) },
                            onDelete = { viewModel.deletePlaylist(playlistWithEntries) }
                        )
                        if (playlistWithEntries != playlists.last()) {
                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.clearNewPlaylistDraft()
                onNewPlaylistDialogDismiss()
            },
            title = { Text(stringResource(R.string.new_playlist)) },
            text = {
                OutlinedTextField(
                    value = viewModel.newPlaylistName,
                    onValueChange = { viewModel.updateNewPlaylistName(it) },
                    placeholder = { Text(stringResource(R.string.playlist_name_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = viewModel.newPlaylistName.isNotBlank(),
                    onClick = {
                        viewModel.createPlaylistFromDraft { id ->
                            onNavigateToDetail(id)
                        }
                        onNewPlaylistDialogDismiss()
                    }
                ) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.clearNewPlaylistDraft()
                    onNewPlaylistDialogDismiss()
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    viewModel.playlistToRename?.let {
        AlertDialog(
            onDismissRequest = { viewModel.cancelRenamePlaylist() },
            title = { Text(stringResource(R.string.rename_playlist)) },
            text = {
                OutlinedTextField(
                    value = viewModel.renamePlaylistName,
                    onValueChange = { viewModel.updateRenamePlaylistName(it) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = viewModel.renamePlaylistName.isNotBlank(),
                    onClick = { viewModel.confirmRenamePlaylist() }
                ) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRenamePlaylist() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PlaylistRow(
    playlistWithEntries: PlaylistWithEntries,
    editMode: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val count = playlistWithEntries.entries.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (editMode) onRename else onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlistWithEntries.playlist.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "$count ${if (count == 1) "song" else "songs"}",
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
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
