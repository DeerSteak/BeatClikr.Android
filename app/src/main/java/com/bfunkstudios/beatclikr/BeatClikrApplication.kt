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
import com.bfunkstudios.beatclikr.services.SecondaryOutputCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BeatClikrApplication : Application() {

    @Inject lateinit var secondaryOutputs: SecondaryOutputCoordinator
    @Inject lateinit var audioPlayerService: IAudioPlayerService
    @Inject lateinit var prefs: IAppPreferences

    override fun onCreate() {
        super.onCreate()
        audioPlayerService.soundBank = prefs.soundBank
        if (prefs.soundBank == SoundBank.ACOUSTIC) {
            audioPlayerService.prepareAudioTrackSounds(prefs.audioTrackSoundCacheSet())
        }
        audioPlayerService.prewarmAudioTrack()
        secondaryOutputs.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                secondaryOutputs.setVisible(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                secondaryOutputs.setVisible(false)
                stopResources()
            }
        })

        @Suppress("DEPRECATION")
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) stopResources()
            }
            override fun onConfigurationChanged(newConfig: Configuration) {}
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onLowMemory() = stopResources()
        })
    }

    private fun stopResources() {
        audioPlayerService.stopPlayback()
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
