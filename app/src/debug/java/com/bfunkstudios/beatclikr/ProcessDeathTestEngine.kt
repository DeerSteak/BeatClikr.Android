package com.bfunkstudios.beatclikr

import android.content.Context
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.services.ActiveSoundConfiguration
import com.bfunkstudios.beatclikr.services.AudioBackendType
import com.bfunkstudios.beatclikr.services.AudioOutputRoute
import com.bfunkstudios.beatclikr.services.CommittedPlaybackConfiguration
import com.bfunkstudios.beatclikr.services.FrameAudioMetricsSnapshot
import com.bfunkstudios.beatclikr.services.FrameAudioRenderedEventBatch
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngineDelegate
import com.bfunkstudios.beatclikr.services.PlaybackEnginePort
import com.bfunkstudios.beatclikr.services.PlaybackEngineStartEvidence
import com.bfunkstudios.beatclikr.services.PlaybackEngineTransportObserver
import com.bfunkstudios.beatclikr.services.PlaybackEngineUpdateResult
import com.bfunkstudios.beatclikr.services.PlaybackInterruptionReason
import com.bfunkstudios.beatclikr.services.PlaybackMode
import com.bfunkstudios.beatclikr.services.PlaybackSessionId
import com.bfunkstudios.beatclikr.services.PolyrhythmAudioEngineDelegate
import com.bfunkstudios.beatclikr.services.SoundPreparationFailure
import com.bfunkstudios.beatclikr.services.SoundPreparationPublication
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class ProcessDeathTestEngine private constructor(private val mode: String) : PlaybackEnginePort {
    private val soundReads = AtomicInteger()
    private val preparingBlock = CountDownLatch(1)
    val startCount = AtomicInteger()
    val stopCount = AtomicInteger()
    @Volatile var focusHeld = false

    override var soundPreparationObserver: ((SoundPreparationPublication) -> Unit)? = null
    override var transportObserver: PlaybackEngineTransportObserver? = null
    override var delegate: MetronomeAudioEngineDelegate? = null
    override var polyrhythmDelegate: PolyrhythmAudioEngineDelegate? = null
    override var isMuted = false

    override fun activeSoundConfiguration(): ActiveSoundConfiguration {
        if (mode == PREPARING && soundReads.incrementAndGet() > 1) preparingBlock.await()
        return SOUNDS
    }

    override fun soundPreparationFailure(): SoundPreparationFailure? = null
    override fun prewarmAudioTrack() = Unit
    override fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot? = null
    override fun release() = preparingBlock.countDown()

    override fun beginStandardSession(
        sessionId: PlaybackSessionId,
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>?,
        alternateSixteenth: Boolean
    ) {
        startCount.incrementAndGet()
        focusHeld = true
        when (mode) {
            PLAYING, STOPPING -> transportObserver?.engineStarted(startEvidence(sessionId))
            INTERRUPTED -> {
                transportObserver?.engineStarted(startEvidence(sessionId))
                transportObserver?.engineInterrupted(
                    sessionId,
                    PlaybackInterruptionReason.AudioFocusLost
                )
            }
            FAILED -> transportObserver?.engineStartFailed(sessionId, "process-death probe")
        }
    }

    override fun beginPolyrhythmSession(
        sessionId: PlaybackSessionId,
        bpm: Float,
        beats: Int,
        against: Int
    ) = beginStandardSession(sessionId, bpm, beats, null, false)

    override fun updateStandardSession(
        sessionId: PlaybackSessionId,
        configuration: CommittedPlaybackConfiguration.Standard,
        completion: (PlaybackEngineUpdateResult) -> Unit
    ) = completion(PlaybackEngineUpdateResult.Accepted(sessionId))

    override fun updatePolyrhythmSession(
        sessionId: PlaybackSessionId,
        configuration: CommittedPlaybackConfiguration.Polyrhythm,
        completion: (PlaybackEngineUpdateResult) -> Unit
    ) = completion(PlaybackEngineUpdateResult.Accepted(sessionId))

    override fun stopSession(sessionId: PlaybackSessionId, mode: PlaybackMode) {
        stopCount.incrementAndGet()
        focusHeld = false
        if (this.mode !in setOf(STOPPING, INTERRUPTED, FAILED)) {
            transportObserver?.engineStopped(sessionId)
        }
    }

    override fun drainRenderedEvents(afterCaptureSequence: Long): FrameAudioRenderedEventBatch? =
        null

    override fun selectSounds(
        requestSequence: Long,
        beatResourceId: Int,
        rhythmResourceId: Int
    ) = Unit

    override fun selectSoundBank(requestSequence: Long, bank: SoundBank) = Unit
    override fun prepareSounds(requestSequence: Long, sounds: Collection<SoundFile>) = Unit

    private fun startEvidence(sessionId: PlaybackSessionId) = PlaybackEngineStartEvidence(
        sessionId,
        SOUNDS,
        AudioOutputRoute.BUILT_IN,
        AudioBackendType.AUDIO_TRACK,
        0
    )

    companion object {
        const val NORMAL = "NORMAL"
        const val PREPARING = "Preparing"
        const val STARTING = "Starting"
        const val PLAYING = "Playing"
        const val STOPPING = "Stopping"
        const val INTERRUPTED = "Interrupted"
        const val FAILED = "Failed"
        const val PREFS = "process_death_probe"
        const val MODE = "mode"

        private val SOUNDS = ActiveSoundConfiguration(
            SoundBank.ACOUSTIC,
            SoundFile.CLICK_HI,
            SoundFile.CLICK_LO
        )

        @JvmStatic
        fun create(context: Context): PlaybackEnginePort? {
            val mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(MODE, NORMAL)
            return mode?.takeUnless { it == NORMAL }?.let(::ProcessDeathTestEngine)
        }
    }
}
