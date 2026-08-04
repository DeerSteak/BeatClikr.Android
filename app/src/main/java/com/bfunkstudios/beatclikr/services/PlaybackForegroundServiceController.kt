package com.bfunkstudios.beatclikr.services

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.bfunkstudios.beatclikr.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

interface PlaybackForegroundServiceGateway {
    fun start()
    fun stop()
}

@Singleton
class AndroidPlaybackForegroundServiceGateway @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PlaybackForegroundServiceGateway {
    override fun start() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, PlaybackForegroundService::class.java)
        )
    }

    override fun stop() {
        context.stopService(Intent(context, PlaybackForegroundService::class.java))
    }
}

@Singleton
class PlaybackForegroundServiceController @Inject constructor(
    private val playback: PlaybackObservation,
    private val control: IAudioPlayerService,
    private val gateway: PlaybackForegroundServiceGateway,
    private val failures: OperationalFailureReporter,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private var collectionJob: Job? = null

    @Synchronized
    fun start() {
        if (collectionJob != null) return
        collectionJob = scope.launch {
            playback.transportState
                .map { it is PlaybackTransportState.SessionState }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) startServiceOrStopPlayback() else stopService()
                }
        }
    }

    private fun startServiceOrStopPlayback() {
        try {
            gateway.start()
        } catch (failure: RuntimeException) {
            failures.report(foregroundServiceFailure())
            control.submit(PlaybackIntent.Stop)
        }
    }

    private fun stopService() {
        try {
            gateway.stop()
        } catch (failure: RuntimeException) {
            failures.report(foregroundServiceFailure())
        }
    }

    private fun foregroundServiceFailure() = OperationalFailure(
        FailureDomain.FOREGROUND_SERVICE,
        "foreground_service_unavailable",
        FailureDisposition.USER_ACTIONABLE,
        FailureRecoveryAction.NONE
    )
}
