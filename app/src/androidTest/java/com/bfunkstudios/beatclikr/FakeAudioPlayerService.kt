package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.FrameAudioMetricsSnapshot
import com.bfunkstudios.beatclikr.services.ActiveSoundConfiguration
import com.bfunkstudios.beatclikr.services.AudioBackendType
import com.bfunkstudios.beatclikr.services.AudioOutputRoute
import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.CommittedPlaybackConfiguration
import com.bfunkstudios.beatclikr.services.PlaybackCommittedEvent
import com.bfunkstudios.beatclikr.services.PlaybackMode
import com.bfunkstudios.beatclikr.services.PlaybackFailureReason
import com.bfunkstudios.beatclikr.services.PlaybackInterruptionReason
import com.bfunkstudios.beatclikr.services.PlaybackIntent
import com.bfunkstudios.beatclikr.services.PlaybackObservation
import com.bfunkstudios.beatclikr.services.PlaybackSessionContext
import com.bfunkstudios.beatclikr.services.PlaybackSessionId
import com.bfunkstudios.beatclikr.services.PlaybackStartOrigin
import com.bfunkstudios.beatclikr.services.PlaybackTransportState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAudioPlayerService : IAudioPlayerService, PlaybackObservation {
    private var isMuted = false

    var startCount = 0
    var stopCount = 0
    var polyrhythmStartCount = 0
    var polyrhythmStopCount = 0
    private var nextSessionId = 1L

    override val transportState = MutableStateFlow<PlaybackTransportState>(
        PlaybackTransportState.Idle
    )
    override val committedEvents = MutableSharedFlow<PlaybackCommittedEvent>()

    override fun submit(intent: PlaybackIntent): Long {
        when (intent) {
            is PlaybackIntent.SetMuted -> isMuted = intent.muted
            is PlaybackIntent.StartStandard -> startStandard(intent)
            is PlaybackIntent.ReplaceStandard -> startStandard(intent)
            is PlaybackIntent.StartPolyrhythm -> startPolyrhythm(intent)
            is PlaybackIntent.StopIfCurrent -> stopIfCurrent(intent.expectedSessionId)
            PlaybackIntent.Stop -> stopPlayback()
            else -> Unit
        }
        return 0
    }

    private fun startStandard(intent: PlaybackIntent.StartStandard) {
        startCount++
        transportState.value = preparing(
            PlaybackMode.STANDARD,
            CommittedPlaybackConfiguration.Standard(
                intent.bpm,
                intent.subdivisions,
                intent.accentPattern,
                intent.alternateSixteenth,
                isMuted
            )
        )
    }

    private fun startStandard(intent: PlaybackIntent.ReplaceStandard) =
        startStandard(
            PlaybackIntent.StartStandard(
                intent.bpm,
                intent.subdivisions,
                intent.accentPattern,
                intent.alternateSixteenth,
                intent.practiceItem
            )
        )

    private fun stopIfCurrent(expectedSessionId: PlaybackSessionId) {
        val current = transportState.value as? PlaybackTransportState.SessionState ?: return
        if (current.context.sessionId != expectedSessionId) return
        recordStop(current.context.mode)
    }
    private fun stopPlayback() {
        val current = transportState.value as? PlaybackTransportState.SessionState ?: return
        recordStop(current.context.mode)
    }

    fun stopPlaybackForTest() = stopPlayback()

    fun startMetronomeForTest(bpm: Float, subdivisions: Int) {
        submit(PlaybackIntent.StartStandard(bpm, subdivisions, null, false))
    }
    private fun startPolyrhythm(intent: PlaybackIntent.StartPolyrhythm) {
        polyrhythmStartCount++
        transportState.value = preparing(
            PlaybackMode.POLYRHYTHM,
            CommittedPlaybackConfiguration.Polyrhythm(
                intent.bpm,
                intent.beats,
                intent.against,
                isMuted
            )
        )
    }
    private fun recordStop(mode: PlaybackMode) {
        if (mode == PlaybackMode.STANDARD) stopCount++
        if (mode == PlaybackMode.POLYRHYTHM) polyrhythmStopCount++
        transportState.value = PlaybackTransportState.Idle
    }
    override fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot? = null
    override fun release() {}

    fun resetCallCounts() {
        startCount = 0
        stopCount = 0
        polyrhythmStartCount = 0
        polyrhythmStopCount = 0
    }

    fun publishPlaying(
        mode: PlaybackMode,
        route: AudioOutputRoute = AudioOutputRoute.BUILT_IN
    ) {
        val context = contextFor(mode).copy(
            audibleSounds = ActiveSoundConfiguration(
                SoundBank.ACOUSTIC,
                SoundFile.CLICK_HI,
                SoundFile.CLICK_LO
            ),
            route = route,
            backend = AudioBackendType.AUDIO_TRACK
        )
        transportState.value = PlaybackTransportState.Playing(context)
    }

    fun publishFailed(mode: PlaybackMode, reason: PlaybackFailureReason) {
        transportState.value = PlaybackTransportState.Failed(contextFor(mode), reason)
    }

    fun publishInterrupted(mode: PlaybackMode, reason: PlaybackInterruptionReason) {
        transportState.value = PlaybackTransportState.Interrupted(contextFor(mode), reason)
    }

    private fun contextFor(mode: PlaybackMode): PlaybackSessionContext {
        val current = transportState.value as? PlaybackTransportState.SessionState
        if (current?.context?.mode == mode) return current.context
        return preparing(
            mode,
            if (mode == PlaybackMode.STANDARD) {
                CommittedPlaybackConfiguration.Standard(120f, 4, null, false, isMuted)
            } else {
                CommittedPlaybackConfiguration.Polyrhythm(120f, 3, 2, isMuted)
            }
        ).context
    }

    private fun preparing(
        mode: PlaybackMode,
        configuration: CommittedPlaybackConfiguration
    ): PlaybackTransportState.Preparing = PlaybackTransportState.Preparing(
        PlaybackSessionContext(
            PlaybackSessionId(nextSessionId++),
            mode,
            configuration,
            startOrigin = PlaybackStartOrigin.USER
        )
    )
}
