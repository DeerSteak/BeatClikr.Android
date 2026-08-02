package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.services.MetronomeAudioEngineDelegate
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngine
import com.bfunkstudios.beatclikr.services.PlaybackMode
import com.bfunkstudios.beatclikr.services.PlaybackSessionId
import com.bfunkstudios.beatclikr.services.PolyrhythmAudioEngineDelegate
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

abstract class MetronomeTestDelegate : MetronomeAudioEngineDelegate

abstract class PolyrhythmTestDelegate : PolyrhythmAudioEngineDelegate

private data class TestSession(val id: PlaybackSessionId, val mode: PlaybackMode)
private val nextTestSession = AtomicLong(1)
private val testSessions = WeakHashMap<MetronomeAudioEngine, TestSession>()

fun MetronomeAudioEngine.startMetronome(
    bpm: Float,
    subdivisions: Int,
    accentPattern: List<Boolean>?,
    alternateSixteenth: Boolean,
    delegate: MetronomeAudioEngineDelegate
) {
    val session = TestSession(PlaybackSessionId(nextTestSession.getAndIncrement()), PlaybackMode.STANDARD)
    synchronized(testSessions) { testSessions[this] = session }
    startMetronome(bpm, subdivisions, accentPattern, alternateSixteenth, delegate, session.id) { _, _ -> }
}

fun MetronomeAudioEngine.stopMetronome() = stopTestSession(PlaybackMode.STANDARD)

fun MetronomeAudioEngine.startPolyrhythm(bpm: Float, beats: Int, against: Int) {
    val session = TestSession(PlaybackSessionId(nextTestSession.getAndIncrement()), PlaybackMode.POLYRHYTHM)
    synchronized(testSessions) { testSessions[this] = session }
    startPolyrhythm(session.id, bpm, beats, against) { _, _ -> }
}

fun MetronomeAudioEngine.stopPolyrhythm() = stopTestSession(PlaybackMode.POLYRHYTHM)

private fun MetronomeAudioEngine.stopTestSession(mode: PlaybackMode) {
    val session = synchronized(testSessions) { testSessions.remove(this) }
    require(session?.mode == mode) { "No active $mode test session" }
    stopSession(session.id, mode) {}
}
