@file:OptIn(ExperimentalMaterial3Api::class)

package com.bfunkstudios.beatclikr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.ui.components.SongDetail
import com.bfunkstudios.beatclikr.ui.components.SongLibraryView
import java.util.UUID

// --- Navigation destinations ---

private const val ROUTE_INSTANT = "instant"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_PLAYLIST = "playlist"
private const val ROUTE_PLAYLIST_DETAIL = "playlist_detail/{playlistId}"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_SETTINGS = "settings"
private const val ARG_PLAYLIST_ID = "playlistId"

private sealed class AppTab(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector? = null,
    val iconRes: Int? = null
) {
    object Instant : AppTab(ROUTE_INSTANT, R.string.tab_instant, iconRes = R.drawable.metronome_tab_icon)
    object Library : AppTab(ROUTE_LIBRARY, R.string.tab_library, Icons.AutoMirrored.Filled.FeaturedPlayList)
    object Playlist : AppTab(ROUTE_PLAYLIST, R.string.tab_playlist, Icons.Filled.PlaylistPlay)
    object History : AppTab(ROUTE_HISTORY, R.string.tab_history, Icons.Filled.CalendarMonth)
    object Settings : AppTab(ROUTE_SETTINGS, R.string.tab_settings, Icons.Filled.Settings)

    companion object {
        val all = listOf(Instant, Library, Playlist, History, Settings)
    }
}

// --- Top app bar ---

@Composable
private fun BeatClikrAppBar(
    title: String,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier,
        navigationIcon = {
            when {
                canNavigateBack -> IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                leadingContent != null -> leadingContent()
            }
        },
        actions = actions
    )
}

// --- Root composable ---

@Composable
fun BeatClikrApp(
    navController: NavHostController = rememberNavController(),
    songLibraryViewModel: SongLibraryViewModel = hiltViewModel(),
    metronomeViewModel: MetronomeViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    practiceHistoryViewModel: PracticeHistoryViewModel = hiltViewModel(),
    onAlwaysUseDarkThemeChange: (Boolean) -> Unit = {}
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val isTopLevel = AppTab.all.any { it.route == currentRoute }

    val uiState by songLibraryViewModel.uiState.collectAsState()
    val hasSongs = uiState.songList.isNotEmpty()
    val playlists by playlistViewModel.playlists.collectAsState()
    val selectedPlaylist by playlistViewModel.selectedPlaylist.collectAsState()

    var editMode by remember { mutableStateOf(false) }
    var showSongDetail by remember { mutableStateOf(false) }
    val songDetailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var playlistListEditMode by remember { mutableStateOf(false) }
    var playlistDetailEditMode by remember { mutableStateOf(false) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var showSongPickerForPlaylist by remember { mutableStateOf(false) }
    var showFocusView by remember { mutableStateOf(false) }

    LaunchedEffect(showSongDetail) {
        if (showSongDetail) songLibraryViewModel.initDraft(uiState.selectedSong)
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != ROUTE_PLAYLIST) playlistListEditMode = false
        if (currentRoute != ROUTE_PLAYLIST_DETAIL) playlistDetailEditMode = false
        if (currentRoute != ROUTE_PLAYLIST && currentRoute != ROUTE_PLAYLIST_DETAIL) {
            showNewPlaylistDialog = false
            showSongPickerForPlaylist = false
            showFocusView = false
        }
    }

    val appBarTitle = when (currentRoute) {
        ROUTE_INSTANT -> stringResource(R.string.instant_metronome)
        ROUTE_LIBRARY -> stringResource(R.string.song_library)
        ROUTE_PLAYLIST -> stringResource(R.string.tab_playlist)
        ROUTE_PLAYLIST_DETAIL -> selectedPlaylist?.playlist?.name ?: stringResource(R.string.tab_playlist)
        ROUTE_HISTORY -> stringResource(R.string.practice_history)
        ROUTE_SETTINGS -> stringResource(R.string.settings)
        else -> stringResource(R.string.app_name)
    }

    Scaffold(
        topBar = {
            if (currentRoute != ROUTE_INSTANT) {
                BeatClikrAppBar(
                    title = appBarTitle,
                    canNavigateBack = !isTopLevel,
                    navigateUp = { navController.popBackStack() },
                    actions = {
                        if (currentRoute == ROUTE_LIBRARY) {
                            IconButton(onClick = {
                                songLibraryViewModel.setSelectedSong(null)
                                showSongDetail = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.add_song),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (hasSongs) {
                                TextButton(onClick = { editMode = !editMode }) {
                                    Text(if (editMode) stringResource(R.string.done) else stringResource(R.string.edit))
                                }
                            }
                        }
                        if (currentRoute == ROUTE_PLAYLIST) {
                            IconButton(onClick = { showNewPlaylistDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.new_playlist),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (playlists.isNotEmpty()) {
                                TextButton(onClick = { playlistListEditMode = !playlistListEditMode }) {
                                    Text(if (playlistListEditMode) stringResource(R.string.done) else stringResource(R.string.edit))
                                }
                            }
                        }
                        if (currentRoute == ROUTE_PLAYLIST_DETAIL) {
                            val entries = viewModel@ playlistViewModel.sortedEntries(selectedPlaylist)
                            if (!playlistDetailEditMode && entries.isNotEmpty()) {
                                IconButton(onClick = { showFocusView = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Focus View",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = { showSongPickerForPlaylist = true }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.add_song),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            TextButton(onClick = { playlistDetailEditMode = !playlistDetailEditMode }) {
                                Text(if (playlistDetailEditMode) stringResource(R.string.done) else stringResource(R.string.edit))
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                AppTab.all.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val contentDescription = stringResource(tab.titleRes)
                            tab.iconRes?.let { iconRes ->
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = contentDescription
                                )
                            } ?: Icon(
                                imageVector = checkNotNull(tab.icon),
                                contentDescription = contentDescription
                            )
                        },
                        label = { Text(stringResource(tab.titleRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_INSTANT,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_INSTANT) {
                MetronomeContainerView(metronomeViewModel = metronomeViewModel)
            }
            composable(ROUTE_LIBRARY) {
                SongLibraryView(
                    uiState = uiState,
                    viewModel = songLibraryViewModel,
                    editMode = editMode,
                    isPlaying = metronomeViewModel.isPlaying,
                    beatPulse = metronomeViewModel.beatPulse,
                    onPlayPause = { metronomeViewModel.togglePlayPause() },
                    onPlaySong = { song ->
                        songLibraryViewModel.markSongPlaying(song)
                        metronomeViewModel.playSong(song)
                    },
                    navigateToDetail = { showSongDetail = true }
                )
            }
            composable(ROUTE_PLAYLIST) {
                PlaylistListView(
                    viewModel = playlistViewModel,
                    editMode = playlistListEditMode,
                    showNewPlaylistDialog = showNewPlaylistDialog,
                    onNewPlaylistDialogDismiss = { showNewPlaylistDialog = false },
                    onNavigateToDetail = { playlistId ->
                        playlistViewModel.selectPlaylist(playlistId)
                        navController.navigate("playlist_detail/$playlistId")
                    }
                )
            }
            composable(
                route = ROUTE_PLAYLIST_DETAIL,
                arguments = listOf(navArgument(ARG_PLAYLIST_ID) { type = NavType.StringType })
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments
                    ?.getString(ARG_PLAYLIST_ID)
                    ?.let { UUID.fromString(it) }
                LaunchedEffect(playlistId) {
                    if (playlistId != null) playlistViewModel.selectPlaylist(playlistId)
                }
                PlaylistDetailView(
                    viewModel = playlistViewModel,
                    editMode = playlistDetailEditMode,
                    showSongPicker = showSongPickerForPlaylist,
                    onSongPickerDismiss = { showSongPickerForPlaylist = false },
                    isPlaying = metronomeViewModel.isPlaying,
                    beatPulse = metronomeViewModel.beatPulse,
                    onPlayPause = { metronomeViewModel.togglePlayPause() },
                    onPlaySong = { song -> metronomeViewModel.playSong(song) },
                    onEditSong = { song ->
                        songLibraryViewModel.setSelectedSong(song.id)
                        showSongDetail = true
                    }
                )
            }
            composable(ROUTE_HISTORY) {
                PracticeHistoryView(viewModel = practiceHistoryViewModel)
            }
            composable(ROUTE_SETTINGS) {
                SettingsView(
                    metronomeViewModel = metronomeViewModel,
                    onAlwaysUseDarkThemeChange = onAlwaysUseDarkThemeChange
                )
            }
        }
    }

    if (showFocusView) {
        PlaylistFocusView(
            viewModel = playlistViewModel,
            isPlaying = metronomeViewModel.isPlaying,
            beatPulse = metronomeViewModel.beatPulse,
            onPlayPause = { metronomeViewModel.togglePlayPause() },
            onPlaySong = { song -> metronomeViewModel.playSong(song) },
            onDismiss = { showFocusView = false }
        )
    }

    if (showSongDetail) {
        ModalBottomSheet(
            onDismissRequest = { showSongDetail = false },
            sheetState = songDetailSheetState
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showSongDetail = false }) {
                    Text(stringResource(R.string.cancel))
                }
                Text(
                    text = stringResource(R.string.song_detail),
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(
                    onClick = {
                        songLibraryViewModel.saveDraft()
                        showSongDetail = false
                    },
                    enabled = songLibraryViewModel.isDraftValid
                ) {
                    Text(stringResource(R.string.save))
                }
            }
            SongDetail(viewModel = songLibraryViewModel)
        }
    }
}
