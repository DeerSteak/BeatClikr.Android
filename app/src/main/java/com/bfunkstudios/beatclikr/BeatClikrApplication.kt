package com.bfunkstudios.beatclikr

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.IFlashlightService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BeatClikrApplication : Application() {

    @Inject lateinit var flashlightService: IFlashlightService
    @Inject lateinit var audioPlayerService: IAudioPlayerService
    @Inject lateinit var prefs: IAppPreferences

    override fun onCreate() {
        super.onCreate()
        if (prefs.useAudioTrack) {
            audioPlayerService.prewarmAudioTrack()
        }
        // BeatClikr is foreground-only: no foreground service is used, so playback
        // stops when the app leaves the foreground. This is intentional.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
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
        audioPlayerService.stopMetronome()
        audioPlayerService.stopPolyrhythm()
        flashlightService.turnFlashlightOff()
    }
}
