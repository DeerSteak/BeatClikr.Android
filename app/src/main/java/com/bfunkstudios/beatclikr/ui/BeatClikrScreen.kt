@file:OptIn(ExperimentalMaterial3Api::class)

package com.bfunkstudios.beatclikr.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bfunkstudios.beatclikr.R

@Composable
fun BeatClikrAppBar(
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(id = R.string.app_name)
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
                        contentDescription = stringResource(id = R.string.back)
                    )
                }
            }
        }
    )
}

@Composable
fun BeatClikrApp(
    songLibraryViewModel: SongLibraryViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController()
) {
    Scaffold(
        topBar = {
            BeatClikrAppBar(
                canNavigateBack = false,
                navigateUp = { /*TODO*/ },
                title = stringResource(R.string.instant_metronome)
            )
        }
    ) { innerPadding ->
        val uiState by songLibraryViewModel.uiState.collectAsState()

        NavHost(
            navController = navController,
            startDestination = BeatClikrScreen.InstantMetronome.name,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BeatClikrScreen.InstantMetronome.name){
                InstantMetronomeView()
            }
        }
    }

}

enum class BeatClikrScreen() {
    SongList, SongDetails, InstantMetronome
}