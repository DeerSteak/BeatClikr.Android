package com.bfunkstudios.beatclikr

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.services.IPracticeReminderScheduler
import com.bfunkstudios.beatclikr.services.PlaybackObservation
import com.bfunkstudios.beatclikr.services.PlaybackTransportState
import com.bfunkstudios.beatclikr.ui.BeatClikrApp
import com.bfunkstudios.beatclikr.ui.theme.BeatClikrTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var prefs: IAppPreferences
    @Inject lateinit var reminderScheduler: IPracticeReminderScheduler
    @Inject lateinit var playback: PlaybackObservation

    private var keepScreenAwakePreference = false
    private var transportState: PlaybackTransportState = PlaybackTransportState.Idle
    private var appVisible = false

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { reminderScheduler.rescheduleIfEnabled() }
    }

    override fun onStart() {
        super.onStart()
        appVisible = true
        updateKeepScreenOn()
    }

    override fun onStop() {
        appVisible = false
        updateKeepScreenOn()
        super.onStop()
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        keepScreenAwakePreference = prefs.keepScreenAwake
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playback.transportState.collect { state ->
                    transportState = state
                    updateKeepScreenOn()
                }
            }
        }
        setContent {
            var forceDarkTheme by remember { mutableStateOf(prefs.alwaysUseDarkTheme) }

            BeatClikrTheme(forceDarkTheme = forceDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BeatClikrApp(
                        onAlwaysUseDarkThemeChange = { forceDarkTheme = it },
                        onKeepScreenAwakeChange = {
                            keepScreenAwakePreference = it
                            updateKeepScreenOn()
                        }
                    )
                }
            }
        }
    }

    private fun updateKeepScreenOn() {
        val shouldKeepOn = keepScreenAwakePreference && appVisible &&
            transportState is PlaybackTransportState.Playing
        if (shouldKeepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
