@file:OptIn(ExperimentalMaterial3Api::class)

package com.bfunkstudios.beatclikr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

// --- Root composable ---

@Composable
fun BeatClikrApp(
    navController: NavHostController = rememberNavController(),
    songLibraryViewModel: SongLibraryViewModel = hiltViewModel(),
    metronomeViewModel: MetronomeViewModel = hiltViewModel(),
    polyrhythmViewModel: PolyrhythmViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    practiceHistoryViewModel: PracticeHistoryViewModel = hiltViewModel(),
    onAlwaysUseDarkThemeChange: (Boolean) -> Unit = {},
    onKeepScreenAwakeChange: (Boolean) -> Unit = {}
) {
    BoxWithConstraints {
        val useSidebar = maxWidth >= 600.dp

        BeatClikrNavigationContent(
            navController = navController,
            songLibraryViewModel = songLibraryViewModel,
            metronomeViewModel = metronomeViewModel,
            polyrhythmViewModel = polyrhythmViewModel,
            playlistViewModel = playlistViewModel,
            practiceHistoryViewModel = practiceHistoryViewModel,
            useSidebar = useSidebar,
            onAlwaysUseDarkThemeChange = onAlwaysUseDarkThemeChange,
            onKeepScreenAwakeChange = onKeepScreenAwakeChange
        )
    }
}

@Composable
private fun BeatClikrNavigationContent(
    navController: NavHostController,
    songLibraryViewModel: SongLibraryViewModel,
    metronomeViewModel: MetronomeViewModel,
    polyrhythmViewModel: PolyrhythmViewModel,
    playlistViewModel: PlaylistViewModel,
    practiceHistoryViewModel: PracticeHistoryViewModel,
    useSidebar: Boolean,
    onAlwaysUseDarkThemeChange: (Boolean) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val appTabs = if (useSidebar) AppTab.expanded else AppTab.compact
    val isTopLevel = appTabs.any { it.route == currentRoute }
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
    var showHistoryShareSheet by remember { mutableStateOf(false) }

    LaunchedEffect(useSidebar, currentRoute) {
        if (!useSidebar && currentRoute == ROUTE_POLYRHYTHM) {
            polyrhythmViewModel.stop()
            navController.navigate(ROUTE_INSTANT) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

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
        ROUTE_POLYRHYTHM -> stringResource(R.string.polyrhythm)
        ROUTE_LIBRARY -> stringResource(R.string.song_library)
        ROUTE_PLAYLIST -> stringResource(R.string.tab_playlist)
        ROUTE_PLAYLIST_DETAIL -> selectedPlaylist?.playlist?.name ?: stringResource(R.string.tab_playlist)
        ROUTE_HISTORY -> stringResource(R.string.practice_history)
        ROUTE_SETTINGS -> stringResource(R.string.settings)
        else -> stringResource(R.string.app_name)
    }

    val contentScaffold: @Composable () -> Unit = {
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
                        if (currentRoute == ROUTE_HISTORY) {
                            IconButton(onClick = { showHistoryShareSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.IosShare,
                                    contentDescription = stringResource(R.string.share_streak),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (currentRoute == ROUTE_PLAYLIST_DETAIL) {
                            val entries = viewModel@ playlistViewModel.sortedEntries(selectedPlaylist)
                            if (!playlistDetailEditMode && entries.isNotEmpty()) {
                                IconButton(onClick = { showFocusView = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = stringResource(R.string.focus_view),
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
            if (!useSidebar) {
                BeatClikrNavigationBar(
                    tabs = appTabs,
                    currentRoute = currentRoute,
                    navController = navController,
                    metronomeViewModel = metronomeViewModel,
                    polyrhythmViewModel = polyrhythmViewModel
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            NavHost(
                navController = navController,
                startDestination = ROUTE_INSTANT,
                modifier = if (useSidebar) {
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = TABLET_CONTENT_MAX_WIDTH)
                        .fillMaxWidth()
                } else {
                    Modifier.fillMaxSize()
                }
            ) {
                composable(ROUTE_INSTANT) {
                    LaunchedEffect(Unit) {
                        metronomeViewModel.returnToInstantMode()
                    }
                    if (useSidebar) {
                        MetronomeView(viewModel = metronomeViewModel)
                    } else {
                        MetronomeContainerView(
                            metronomeViewModel = metronomeViewModel,
                            polyrhythmViewModel = polyrhythmViewModel
                        )
                    }
                }
                composable(ROUTE_POLYRHYTHM) {
                    PolyrhythmView(viewModel = polyrhythmViewModel)
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
                    PracticeHistoryView(
                        viewModel = practiceHistoryViewModel,
                        showShareSheet = showHistoryShareSheet,
                        onShareSheetDismiss = { showHistoryShareSheet = false }
                    )
                }
                composable(ROUTE_SETTINGS) {
                    SettingsView(
                        metronomeViewModel = metronomeViewModel,
                        onAlwaysUseDarkThemeChange = onAlwaysUseDarkThemeChange,
                        onKeepScreenAwakeChange = onKeepScreenAwakeChange
                    )
                }
            }
        }
    }
    }

    if (useSidebar) {
        Row {
            BeatClikrNavigationRail(
                tabs = appTabs,
                currentRoute = currentRoute,
                navController = navController,
                metronomeViewModel = metronomeViewModel,
                polyrhythmViewModel = polyrhythmViewModel
            )
            contentScaffold()
        }
    } else {
        contentScaffold()
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
