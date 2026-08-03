package com.bfunkstudios.beatclikr

import android.util.Log
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.music.PlaybackInputResult
import com.bfunkstudios.beatclikr.services.ActiveSoundConfiguration
import com.bfunkstudios.beatclikr.services.FramePlaybackPublicationBoundary
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngine
import com.bfunkstudios.beatclikr.services.PlaybackMode
import com.bfunkstudios.beatclikr.services.PlaybackSessionId
import com.bfunkstudios.beatclikr.services.RenderedFrameEvent
import com.bfunkstudios.beatclikr.services.SoundPreparationFailure
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val nextTestSession = AtomicLong(1)
private val nextTestRequest = AtomicLong(1)

fun MetronomeAudioEngine.loadSounds(beatResourceId: Int, rhythmResourceId: Int) =
    selectSounds(nextTestRequest.getAndIncrement(), beatResourceId, rhythmResourceId)

fun MetronomeAudioEngine.prewarm() = prewarmAudioTrack()

fun MetronomeAudioEngine.prepareAudioTrackSounds(sounds: Collection<SoundFile>) =
    prepareSounds(nextTestRequest.getAndIncrement(), sounds)

fun MetronomeAudioEngine.getActiveSoundConfiguration(): ActiveSoundConfiguration? =
    activeSoundConfiguration()

fun MetronomeAudioEngine.getSoundPreparationFailure(): SoundPreparationFailure? =
    soundPreparationFailure()

class RenderedEventTestSession private constructor(
    private val engine: MetronomeAudioEngine,
    private val sessionId: PlaybackSessionId,
    private val mode: PlaybackMode,
    private val onRecords: (List<RenderedFrameEvent>, Int) -> Unit
) : AutoCloseable {
    private val polling = Executors.newSingleThreadScheduledExecutor()
    private val failure = AtomicReference<Throwable?>(null)
    private var captureSequence = 0L
    private val pendingFrame = mutableListOf<RenderedFrameEvent>()

    init {
        polling.scheduleAtFixedRate(::poll, 0, 1, TimeUnit.MILLISECONDS)
    }

    override fun close() {
        polling.shutdownNow()
        engine.stopSession(sessionId, mode)
        failure.get()?.let { throw AssertionError("Rendered-event polling failed", it) }
    }

    private fun poll() {
        try {
            val batch = engine.drainRenderedEvents(captureSequence) ?: return
            captureSequence = batch.events.nextCaptureSequence
            val records = batch.events.records.filter { it.sessionId == sessionId.value }
            if (records.isEmpty()) return
            pendingFrame += records
            val newestFrame = pendingFrame.maxOf { it.intendedFrame }
            val complete = pendingFrame.filter { it.intendedFrame < newestFrame }
            pendingFrame.removeAll { it.intendedFrame < newestFrame }
            if (complete.isNotEmpty()) onRecords(complete, batch.sampleRate)
        } catch (problem: Throwable) {
            failure.compareAndSet(null, problem)
            Log.e("RenderedEventTest", "Polling failed", problem)
        }
    }

    companion object {
        fun standard(
            engine: MetronomeAudioEngine,
            bpm: Float,
            subdivisions: Int,
            accentPattern: List<Boolean>?,
            alternateSixteenth: Boolean,
            onRecords: (List<RenderedFrameEvent>, Int) -> Unit
        ): RenderedEventTestSession {
            val validated = FramePlaybackPublicationBoundary.standardConfiguration(
                bpm,
                subdivisions,
                accentPattern,
                alternateSixteenth,
                engine.isMuted
            ) as PlaybackInputResult.Accepted
            val sessionId = PlaybackSessionId(nextTestSession.getAndIncrement())
            engine.beginStandardSession(sessionId, validated.value)
            return RenderedEventTestSession(engine, sessionId, PlaybackMode.STANDARD, onRecords)
        }

        fun polyrhythm(
            engine: MetronomeAudioEngine,
            bpm: Float,
            beats: Int,
            against: Int,
            onRecords: (List<RenderedFrameEvent>, Int) -> Unit
        ): RenderedEventTestSession {
            val validated = FramePlaybackPublicationBoundary.polyrhythmConfiguration(
                bpm,
                beats,
                against,
                engine.isMuted
            ) as PlaybackInputResult.Accepted
            val sessionId = PlaybackSessionId(nextTestSession.getAndIncrement())
            engine.beginPolyrhythmSession(sessionId, validated.value)
            return RenderedEventTestSession(engine, sessionId, PlaybackMode.POLYRHYTHM, onRecords)
        }
    }
}
