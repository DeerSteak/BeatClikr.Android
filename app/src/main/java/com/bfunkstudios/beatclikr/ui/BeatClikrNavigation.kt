@file:OptIn(ExperimentalMaterial3Api::class)

package com.bfunkstudios.beatclikr.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FeaturedPlayList
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.bfunkstudios.beatclikr.R

internal const val ROUTE_INSTANT = "instant"
internal const val ROUTE_POLYRHYTHM = "polyrhythm"
internal const val ROUTE_LIBRARY = "library"
internal const val ROUTE_PLAYLIST = "playlist"
internal const val ROUTE_PLAYLIST_DETAIL = "playlist_detail/{playlistId}"
internal const val ROUTE_HISTORY = "history"
internal const val ROUTE_SETTINGS = "settings"
internal const val ARG_PLAYLIST_ID = "playlistId"
internal val TABLET_CONTENT_MAX_WIDTH = 840.dp

internal sealed class AppTab(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector? = null,
    val iconRes: Int? = null
) {
    data object Instant : AppTab(ROUTE_INSTANT, R.string.tab_instant, iconRes = R.drawable.metronome_tab_icon)
    data object Polyrhythm : AppTab(ROUTE_POLYRHYTHM, R.string.tab_polyrhythm, Icons.Filled.GraphicEq)
    data object Library : AppTab(ROUTE_LIBRARY, R.string.tab_library, Icons.AutoMirrored.Filled.FeaturedPlayList)
    data object Playlist : AppTab(ROUTE_PLAYLIST, R.string.tab_playlist, Icons.AutoMirrored.Filled.QueueMusic)
    data object History : AppTab(ROUTE_HISTORY, R.string.tab_history, Icons.Filled.CalendarMonth)
    data object Settings : AppTab(ROUTE_SETTINGS, R.string.tab_settings, Icons.Filled.Settings)

    companion object {
        val compact = listOf(Instant, Library, Playlist, History, Settings)
        val expanded = listOf(Instant, Polyrhythm, Library, Playlist, History, Settings)
    }
}

@Composable
internal fun BeatClikrAppBar(
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

@Composable
internal fun BeatClikrNavigationBar(
    tabs: List<AppTab>,
    currentRoute: String?,
    navController: NavHostController,
    metronomeViewModel: MetronomeViewModel,
    polyrhythmViewModel: PolyrhythmViewModel
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navigateToTopLevel(
                        tab = tab,
                        navController = navController,
                        currentRoute = currentRoute,
                        metronomeViewModel = metronomeViewModel,
                        polyrhythmViewModel = polyrhythmViewModel
                    )
                },
                icon = { AppTabIcon(tab) },
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

@Composable
internal fun BeatClikrNavigationRail(
    tabs: List<AppTab>,
    currentRoute: String?,
    navController: NavHostController,
    metronomeViewModel: MetronomeViewModel,
    polyrhythmViewModel: PolyrhythmViewModel
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        tabs.forEach { tab ->
            NavigationRailItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navigateToTopLevel(
                        tab = tab,
                        navController = navController,
                        currentRoute = currentRoute,
                        metronomeViewModel = metronomeViewModel,
                        polyrhythmViewModel = polyrhythmViewModel
                    )
                },
                icon = { AppTabIcon(tab) },
                label = { Text(stringResource(tab.titleRes)) },
                colors = NavigationRailItemDefaults.colors(
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

@Composable
private fun AppTabIcon(tab: AppTab) {
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
}

private fun navigateToTopLevel(
    tab: AppTab,
    navController: NavHostController,
    currentRoute: String?,
    metronomeViewModel: MetronomeViewModel,
    polyrhythmViewModel: PolyrhythmViewModel
) {
    if (currentRoute == tab.route) return
    when (tab) {
        AppTab.Instant -> polyrhythmViewModel.stop()
        AppTab.Polyrhythm -> metronomeViewModel.stop()
        else -> Unit
    }
    navController.navigate(tab.route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
