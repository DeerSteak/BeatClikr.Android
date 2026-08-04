package com.bfunkstudios.beatclikr

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.PlaybackIntent
import com.bfunkstudios.beatclikr.services.PlaybackForegroundServiceController
import com.bfunkstudios.beatclikr.services.PracticeAccountingCoordinator
import com.bfunkstudios.beatclikr.services.SecondaryOutputCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BeatClikrApplication : Application() {

    @Inject lateinit var secondaryOutputs: SecondaryOutputCoordinator
    @Inject lateinit var audioPlayerService: IAudioPlayerService
    @Inject lateinit var prefs: IAppPreferences
    @Inject lateinit var practiceAccounting: PracticeAccountingCoordinator
    @Inject lateinit var playbackServiceController: PlaybackForegroundServiceController

    override fun onCreate() {
        super.onCreate()
        audioPlayerService.submit(PlaybackIntent.SelectSoundBank(prefs.soundBank))
        if (prefs.soundBank == SoundBank.ACOUSTIC) {
            audioPlayerService.submit(PlaybackIntent.PrepareSounds(prefs.audioTrackSoundCacheSet()))
        }
        audioPlayerService.submit(PlaybackIntent.Prewarm)
        practiceAccounting.start()
        playbackServiceController.start()
        secondaryOutputs.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            SecondaryOutputProcessLifecycleObserver(secondaryOutputs)
        )

        @Suppress("DEPRECATION")
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) stopSecondaryOutputs()
            }
            override fun onConfigurationChanged(newConfig: Configuration) {}
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onLowMemory() = stopSecondaryOutputs()
        })
    }

    private fun stopSecondaryOutputs() {
        secondaryOutputs.stopEffects()
    }

    private fun IAppPreferences.audioTrackSoundCacheSet() = listOf(
        instantBeatSound,
        instantRhythmSound,
        polyrhythmBeatSound,
        polyrhythmRhythmSound,
        playlistBeatSound,
        playlistRhythmSound
    )
}

internal class SecondaryOutputProcessLifecycleObserver(
    private val secondaryOutputs: SecondaryOutputCoordinator
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        secondaryOutputs.setVisible(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        secondaryOutputs.setVisible(false)
    }
}
