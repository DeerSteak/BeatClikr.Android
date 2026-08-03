package com.bfunkstudios.beatclikr

import android.util.Log
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.music.MusicalEventRole
import com.bfunkstudios.beatclikr.music.PlaybackInputResult
import com.bfunkstudios.beatclikr.services.ActiveSoundConfiguration
import com.bfunkstudios.beatclikr.services.FramePlaybackPublicationBoundary
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngine
import com.bfunkstudios.beatclikr.services.PlaybackMode
import com.bfunkstudios.beatclikr.services.PlaybackSessionId
import com.bfunkstudios.beatclikr.services.SoundPreparationFailure
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

interface MetronomeAudioEngineDelegate {
    fun metronomeBeatFired(isBeat: Boolean, beatInterval: Float, beatTimeNanos: Long = 0L)
}

interface PolyrhythmAudioEngineDelegate {
    fun polyrhythmBeatFired(
        beatFired: Boolean,
        rhythmFired: Boolean,
        beatIndex: Int,
        rhythmIndex: Int,
        stepTimeNanos: Long = 0L,
        beatDurationNanos: Long = 0L,
        rhythmDurationNanos: Long = 0L
    )
}

abstract class MetronomeTestDelegate : MetronomeAudioEngineDelegate
abstract class PolyrhythmTestDelegate : PolyrhythmAudioEngineDelegate

private data class TestSession(
    val id: PlaybackSessionId,
    val mode: PlaybackMode,
    val polling: ScheduledExecutorService
)

private val nextTestSession = AtomicLong(1)
private val nextTestRequest = AtomicLong(1)
private val testSessions = WeakHashMap<MetronomeAudioEngine, TestSession>()
private val pollingFailures = WeakHashMap<MetronomeAudioEngine, AtomicReference<Throwable?>>()

fun MetronomeAudioEngine.loadSounds(beatResourceId: Int, rhythmResourceId: Int) =
    selectSounds(nextTestRequest.getAndIncrement(), beatResourceId, rhythmResourceId)

fun MetronomeAudioEngine.prewarm() = prewarmAudioTrack()

fun MetronomeAudioEngine.prepareAudioTrackSounds(sounds: Collection<SoundFile>) =
    prepareSounds(nextTestRequest.getAndIncrement(), sounds)

fun MetronomeAudioEngine.getActiveSoundConfiguration(): ActiveSoundConfiguration? =
    activeSoundConfiguration()

fun MetronomeAudioEngine.getSoundPreparationFailure(): SoundPreparationFailure? =
    soundPreparationFailure()

fun MetronomeAudioEngine.startMetronome(
    bpm: Float,
    subdivisions: Int,
    accentPattern: List<Boolean>?,
    alternateSixteenth: Boolean,
    delegate: MetronomeAudioEngineDelegate
) {
    val validated = FramePlaybackPublicationBoundary.standardConfiguration(
        bpm,
        subdivisions,
        accentPattern,
        alternateSixteenth,
        isMuted
    ) as PlaybackInputResult.Accepted
    val sessionId = PlaybackSessionId(nextTestSession.getAndIncrement())
    beginStandardSession(sessionId, validated.value)
    startPolling(sessionId, PlaybackMode.STANDARD) { records, sampleRate ->
        records.forEach { event ->
            val isBeat = accentPattern?.getOrNull(event.roleIndex) ?: (event.roleIndex == 0)
            val ticks = accentPattern?.let { pattern -> ticksToNextAccent(pattern, event.roleIndex) }
                ?: subdivisions
            delegate.metronomeBeatFired(
                isBeat,
                ticks * 60f / (bpm * subdivisions),
                event.intendedFrame * 1_000_000_000L / sampleRate
            )
        }
    }
}

fun MetronomeAudioEngine.stopMetronome() = stopTestSession(PlaybackMode.STANDARD)

fun MetronomeAudioEngine.startPolyrhythm(bpm: Float, beats: Int, against: Int) {
    val validated = FramePlaybackPublicationBoundary.polyrhythmConfiguration(
        bpm,
        beats,
        against,
        isMuted
    ) as PlaybackInputResult.Accepted
    val sessionId = PlaybackSessionId(nextTestSession.getAndIncrement())
    beginPolyrhythmSession(sessionId, validated.value)
    var beatIndex = 0
    var rhythmIndex = 0
    startPolling(sessionId, PlaybackMode.POLYRHYTHM) { records, sampleRate ->
        val delegate = polyrhythmTestDelegate ?: return@startPolling
        records.groupBy { it.intendedFrame }.values.forEach { simultaneous ->
            val beat = simultaneous.firstOrNull { it.role == MusicalEventRole.POLYRHYTHM_BEAT }
            val rhythm = simultaneous.firstOrNull { it.role == MusicalEventRole.POLYRHYTHM_RHYTHM }
            beat?.let { beatIndex = it.roleIndex }
            rhythm?.let { rhythmIndex = it.roleIndex }
            delegate.polyrhythmBeatFired(
                beat != null,
                rhythm != null,
                beatIndex,
                rhythmIndex,
                simultaneous.first().intendedFrame * 1_000_000_000L / sampleRate,
                (60_000_000_000.0 / bpm).toLong(),
                (60_000_000_000.0 * against / (bpm * beats)).toLong()
            )
        }
    }
}

private var MetronomeAudioEngine.polyrhythmTestDelegate: PolyrhythmAudioEngineDelegate?
    get() = synchronized(polyrhythmDelegates) { polyrhythmDelegates[this] }
    set(value) {
        synchronized(polyrhythmDelegates) {
            if (value == null) polyrhythmDelegates.remove(this) else polyrhythmDelegates[this] = value
        }
    }

private val polyrhythmDelegates = WeakHashMap<MetronomeAudioEngine, PolyrhythmAudioEngineDelegate>()

fun MetronomeAudioEngine.installPolyrhythmTestDelegate(delegate: PolyrhythmAudioEngineDelegate?) {
    polyrhythmTestDelegate = delegate
}

fun MetronomeAudioEngine.stopPolyrhythm() = stopTestSession(PlaybackMode.POLYRHYTHM)

private fun MetronomeAudioEngine.startPolling(
    sessionId: PlaybackSessionId,
    mode: PlaybackMode,
    publish: (List<com.bfunkstudios.beatclikr.services.RenderedFrameEvent>, Int) -> Unit
) {
    val polling = Executors.newSingleThreadScheduledExecutor()
    synchronized(testSessions) { testSessions[this] = TestSession(sessionId, mode, polling) }
    val failure = AtomicReference<Throwable?>(null)
    synchronized(pollingFailures) { pollingFailures[this] = failure }
    var captureSequence = 0L
    polling.scheduleAtFixedRate({
        try {
            drainRenderedEvents(captureSequence)?.let { batch ->
                captureSequence = batch.events.nextCaptureSequence
                val sessionRecords = batch.events.records.filter {
                    it.sessionId == sessionId.value
                }
                if (sessionRecords.isNotEmpty()) publish(sessionRecords, batch.sampleRate)
            }
        } catch (problem: Throwable) {
            failure.compareAndSet(null, problem)
            Log.e("BeatClikrTestAdapter", "Rendered-event polling failed", problem)
        }
    }, 0, 1, TimeUnit.MILLISECONDS)
}

private fun MetronomeAudioEngine.stopTestSession(mode: PlaybackMode) {
    val session = synchronized(testSessions) { testSessions.remove(this) }
    require(session?.mode == mode) { "No active $mode test session" }
    session.polling.shutdownNow()
    val pollingFailure = synchronized(pollingFailures) { pollingFailures.remove(this) }?.get()
    stopSession(session.id, mode)
    if (pollingFailure != null) throw AssertionError("Rendered-event polling failed", pollingFailure)
}

private fun ticksToNextAccent(pattern: List<Boolean>, index: Int): Int {
    for (offset in 1..pattern.size) {
        if (pattern[(index + offset) % pattern.size]) return offset
    }
    return pattern.size
}
