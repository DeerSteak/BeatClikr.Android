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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.ui.components.SongDetail
import com.bfunkstudios.beatclikr.ui.components.SongLibraryView

// --- Navigation destinations ---

private const val ROUTE_INSTANT = "instant"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_SETTINGS = "settings"

private sealed class AppTab(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector? = null,
    val iconRes: Int? = null
) {
    object Instant : AppTab(ROUTE_INSTANT, R.string.tab_instant, iconRes = R.drawable.metronome_tab_icon)
    object Library : AppTab(ROUTE_LIBRARY, R.string.tab_library, Icons.AutoMirrored.Filled.FeaturedPlayList)
    object Settings : AppTab(ROUTE_SETTINGS, R.string.tab_settings, Icons.Filled.Settings)

    companion object {
        val all = listOf(Instant, Library, Settings)
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
    onAlwaysUseDarkThemeChange: (Boolean) -> Unit = {}
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val isTopLevel = AppTab.all.any { it.route == currentRoute }

    val uiState by songLibraryViewModel.uiState.collectAsState()
    val hasSongs = uiState.songList.isNotEmpty()

    var editMode by remember { mutableStateOf(false) }
    var showSongDetail by remember { mutableStateOf(false) }
    val songDetailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(showSongDetail) {
        if (showSongDetail) songLibraryViewModel.initDraft(uiState.selectedSong)
    }

    val appBarTitle = when (currentRoute) {
        ROUTE_INSTANT -> stringResource(R.string.instant_metronome)
        ROUTE_SETTINGS -> stringResource(R.string.settings)
        else          -> stringResource(R.string.song_library)
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
                        }
                        if (currentRoute == ROUTE_LIBRARY && hasSongs) {
                            TextButton(onClick = { editMode = !editMode }) {
                                Text(if (editMode) stringResource(R.string.done) else stringResource(R.string.edit))
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
            composable(ROUTE_SETTINGS) {
                SettingsView(
                    metronomeViewModel = metronomeViewModel,
                    onAlwaysUseDarkThemeChange = onAlwaysUseDarkThemeChange
                )
            }
        }
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
