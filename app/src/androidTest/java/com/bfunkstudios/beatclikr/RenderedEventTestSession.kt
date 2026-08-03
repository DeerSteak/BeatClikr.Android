package com.bfunkstudios.beatclikr

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.music.PlaybackInputResult
import com.bfunkstudios.beatclikr.services.ActiveSoundConfiguration
import com.bfunkstudios.beatclikr.services.FramePlaybackPublicationBoundary
import com.bfunkstudios.beatclikr.services.FrameAudioMetricsSnapshot
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngine
import com.bfunkstudios.beatclikr.services.PlaybackMode
import com.bfunkstudios.beatclikr.services.PlaybackSessionId
import com.bfunkstudios.beatclikr.services.RenderedFrameEvent
import com.bfunkstudios.beatclikr.services.SoundPreparationFailure
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
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

inline fun withPreparedAudioEngine(
    prewarm: Boolean = false,
    block: (MetronomeAudioEngine) -> Unit
) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val engine = MetronomeAudioEngine(context)
    try {
        engine.loadSounds(
            requireNotNull(SoundFile.CLICK_HI.resourceId),
            requireNotNull(SoundFile.CLICK_LO.resourceId)
        )
        if (prewarm) engine.prewarm()
        block(engine)
    } finally {
        engine.release()
    }
}

fun awaitFrameAudioMetrics(
    engine: MetronomeAudioEngine,
    timeoutMillis: Long = 5_000,
    accepted: (FrameAudioMetricsSnapshot) -> Boolean
): FrameAudioMetricsSnapshot {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    while (System.nanoTime() < deadline) {
        engine.getFrameAudioMetricsSnapshot()?.let { if (accepted(it)) return it }
        Thread.sleep(10)
    }
    return requireNotNull(engine.getFrameAudioMetricsSnapshot()).also {
        check(accepted(it)) { "Timed out waiting for frame-audio metrics" }
    }
}

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
        polling.awaitTermination(1, TimeUnit.SECONDS)
        val stopped = CountDownLatch(1)
        engine.stopSession(sessionId, mode, stopped::countDown)
        check(stopped.await(1, TimeUnit.SECONDS)) { "Timed out stopping rendered-event session" }
        failure.get()?.let { throw AssertionError("Rendered-event polling failed", it) }
    }

    private fun poll() {
        try {
            val batch = engine.drainRenderedEvents(captureSequence) ?: return
            captureSequence = batch.events.nextCaptureSequence
            batch.events.records.forEach { record ->
                if (record.sessionId != sessionId.value) return@forEach
                if (pendingFrame.isNotEmpty() && pendingFrame[0].intendedFrame != record.intendedFrame) {
                    onRecords(pendingFrame.toList(), batch.sampleRate)
                    pendingFrame.clear()
                }
                pendingFrame += record
            }
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
