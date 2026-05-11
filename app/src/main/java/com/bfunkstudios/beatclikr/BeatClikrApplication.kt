package com.bfunkstudios.beatclikr

import android.app.Application
import android.content.res.Configuration
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.IFlashlightService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BeatClikrApplication : Application() {

    @Inject lateinit var flashlightService: IFlashlightService
    @Inject lateinit var audioPlayerService: IAudioPlayerService

    override fun onCreate() {
        super.onCreate()
        // BeatClikr is foreground-only: no foreground service is used, so playback
        // stops when the app leaves the foreground. This is intentional.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                audioPlayerService.stopMetronome()
                audioPlayerService.stopPolyrhythm()
                flashlightService.turnFlashlightOff()
            }
        })
    }
}
