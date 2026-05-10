package com.bfunkstudios.beatclikr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.ui.BeatClikrApp
import com.bfunkstudios.beatclikr.ui.theme.BeatClikrTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var prefs: IAppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var forceDarkTheme by remember { mutableStateOf(prefs.alwaysUseDarkTheme) }

            BeatClikrTheme(forceDarkTheme = forceDarkTheme) {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BeatClikrApp(
                        onAlwaysUseDarkThemeChange = { forceDarkTheme = it }
                    )
                }
            }
        }
    }
}




