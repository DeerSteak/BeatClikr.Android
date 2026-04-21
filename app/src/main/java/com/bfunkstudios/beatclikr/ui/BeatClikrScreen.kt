@file:OptIn(ExperimentalMaterial3Api::class)

package com.bfunkstudios.beatclikr.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.ui.components.SongDetail
import com.bfunkstudios.beatclikr.ui.components.SongList

// --- Navigation destinations ---

private const val ROUTE_INSTANT = "instant"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_SONG_DETAIL = "song_detail"

private sealed class AppTab(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector
) {
    object Instant : AppTab(ROUTE_INSTANT, R.string.tab_instant, Icons.Filled.MusicNote)
    object Library : AppTab(ROUTE_LIBRARY, R.string.tab_library, Icons.AutoMirrored.Filled.List)

    companion object {
        val all = listOf(Instant, Library)
    }
}

// --- Top app bar ---

@Composable
private fun BeatClikrAppBar(
    title: String,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(title) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }
        }
    )
}

// --- Root composable ---

@Composable
fun BeatClikrApp(
    navController: NavHostController = rememberNavController(),
    songLibraryViewModel: SongLibraryViewModel = hiltViewModel()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val isTopLevel = AppTab.all.any { it.route == currentRoute }

    val appBarTitle = when (currentRoute) {
        ROUTE_INSTANT     -> stringResource(R.string.instant_metronome)
        else              -> stringResource(R.string.song_library)
    }

    Scaffold(
        topBar = {
            BeatClikrAppBar(
                title = appBarTitle,
                canNavigateBack = !isTopLevel,
                navigateUp = { navController.popBackStack() }
            )
        },
        bottomBar = {
            NavigationBar {
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
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = stringResource(tab.titleRes)
                            )
                        },
                        label = { Text(stringResource(tab.titleRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val uiState by songLibraryViewModel.uiState.collectAsState()

        NavHost(
            navController = navController,
            startDestination = ROUTE_INSTANT,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_INSTANT) {
                InstantMetronomeView()
            }
            composable(ROUTE_LIBRARY) {
                SongList(uiState, songLibraryViewModel) {
                    navController.navigate(ROUTE_SONG_DETAIL)
                }
            }
            composable(ROUTE_SONG_DETAIL) {
                SongDetail(uiState, songLibraryViewModel) {
                    navController.popBackStack()
                }
            }
        }
    }
}
