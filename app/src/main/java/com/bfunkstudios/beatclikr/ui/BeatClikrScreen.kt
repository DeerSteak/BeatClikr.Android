@file:OptIn(ExperimentalMaterial3Api::class)

package com.bfunkstudios.beatclikr.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.ui.components.SongDetail
import com.bfunkstudios.beatclikr.ui.components.SongList

@Composable
fun BeatClikrAppBar(
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(stringResource(id = R.string.app_name))},
        colors = TopAppBarDefaults.mediumTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.back)
                    )
                }
            }
        }
    )
}

@Composable
fun BeatClikrApp(
    songListViewModel: SongListViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    Scaffold(
        topBar = {
            BeatClikrAppBar(canNavigateBack = false, navigateUp = { /*TODO*/ })
        }
    ) { innerPadding ->
        val uiState by songListViewModel.uiState.collectAsState()

        NavHost(
            navController = navController,
            startDestination = BeatClikrScreen.SongList.name,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BeatClikrScreen.SongList.name){
                SongList(uiState.songList) {
                    navController.navigate(BeatClikrScreen.SongDetails.name)
                }
            }
            composable(BeatClikrScreen.SongDetails.name){
                SongDetail(uiState) {
                    navController.popBackStack()
                }
            }
        }
    }

}

enum class BeatClikrScreen() {
    SongList, SongDetails
}