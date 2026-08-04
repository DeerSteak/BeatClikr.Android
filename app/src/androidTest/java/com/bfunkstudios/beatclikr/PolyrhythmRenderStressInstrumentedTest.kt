package com.bfunkstudios.beatclikr

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.music.MusicalEventRole
import com.bfunkstudios.beatclikr.services.AudioBackendType
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class PolyrhythmRenderStressInstrumentedTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun representativePolyrhythmRemainsStable() {
        val arguments = InstrumentationRegistry.getArguments()
        val durationMinutes = arguments.getString(DURATION_ARGUMENT)
            ?.toIntOrNull()?.coerceIn(1, MAX_DURATION_MINUTES) ?: DEFAULT_DURATION_MINUTES
        val beats = arguments.getString(BEATS_ARGUMENT)
            ?.toIntOrNull()?.coerceIn(1, 15) ?: DEFAULT_BEATS
        val against = arguments.getString(AGAINST_ARGUMENT)
            ?.toIntOrNull()?.coerceIn(1, 15) ?: DEFAULT_AGAINST
        val bpm = arguments.getString(BPM_ARGUMENT)
            ?.toIntOrNull()?.coerceIn(30, 240) ?: DEFAULT_BPM
        val fixture = PolyrhythmContractFixture(beats, against)
        val expected = fixture.eventsBefore(bpm, durationMinutes)
        val latch = CountDownLatch(expected.size)
        val captured = AtomicLong()
        var previousFrame = Long.MIN_VALUE
        val engine = MetronomeAudioEngine(context)
        try {
            engine.loadSounds(
                requireNotNull(SoundFile.CLICK_HI.resourceId),
                requireNotNull(SoundFile.CLICK_LO.resourceId)
            )
            val session = RenderedEventTestSession.polyrhythm(
                engine, bpm.toFloat(), beats, against
            ) { records, _ ->
                records.groupBy { it.intendedFrame }.values.forEach { simultaneous ->
                    val index = captured.getAndIncrement().toInt()
                    if (index >= expected.size) return@forEach
                    val contract = expected[index]
                    val beat = simultaneous.singleOrNull {
                        it.role == MusicalEventRole.POLYRHYTHM_BEAT
                    }
                    val rhythm = simultaneous.singleOrNull {
                        it.role == MusicalEventRole.POLYRHYTHM_RHYTHM
                    }
                    assertTrue("Duplicate or non-monotonic intended frame", simultaneous.first().intendedFrame > previousFrame)
                    assertEquals(index.toLong(), simultaneous.first().eventSequence)
                    assertEquals(contract.beatFired, beat != null)
                    assertEquals(contract.rhythmFired, rhythm != null)
                    beat?.let { assertEquals(contract.beatIndex, it.roleIndex) }
                    rhythm?.let { assertEquals(contract.rhythmIndex, it.roleIndex) }
                    assertTrue(simultaneous.all { it.eventSequence == index.toLong() })
                    previousFrame = simultaneous.first().intendedFrame
                    latch.countDown()
                }
            }
            logProgress(durationMinutes, latch)
            assertTrue(
                "Timed out with ${latch.count} event frames missing",
                latch.await(STOP_GRACE_SECONDS, TimeUnit.SECONDS)
            )
            session.close()
            val metrics = requireNotNull(engine.getFrameAudioMetricsSnapshot())
            val expectedBeatVoices = expected.count { it.beatFired }.toLong()
            val expectedRhythmVoices = expected.count { it.rhythmFired }.toLong()

            Log.i(
                TAG,
                "minutes=$durationMinutes bpm=$bpm ratio=$beats:$against " +
                    "eventFrames=${expected.size} beatVoices=$expectedBeatVoices " +
                    "rhythmVoices=$expectedRhythmVoices backend=${metrics.backend} " +
                    "route=${metrics.route} sampleRate=${metrics.sampleRate} " +
                    "channels=${metrics.channelCount} burstFrames=${metrics.outputFramesPerBuffer} " +
                    "bufferFrames=${metrics.bufferFrames} mode=${metrics.performanceMode} " +
                    "deadlines=${metrics.deadlineMisses} drops=${metrics.droppedEvents} " +
                    "underruns=${metrics.underrunCount} " +
                    "underrunSkippedFrames=${metrics.underrunSkippedFrames} " +
                    "chunks=${metrics.renderedChunks} " +
                    "intendedFrames=${metrics.intendedFrames} renderedFrames=${metrics.renderedFrames} " +
                    "writtenFrames=${metrics.writtenFrames} presentedFrames=${metrics.estimatedPresentedFrames}"
            )

            assertTrue("Captured event-frame count was incomplete", captured.get() >= expected.size)
            assertTrue("Rendered beat voices were missing", metrics.queuedBeatClicks >= expectedBeatVoices)
            assertTrue("Rendered rhythm voices were missing", metrics.queuedRhythmClicks >= expectedRhythmVoices)
            assertEquals(AudioBackendType.AUDIO_TRACK, metrics.backend)
            assertTrue("AudioTrack rendered no chunks", metrics.renderedChunks > 0)
            assertEquals(
                metrics.intendedFrames,
                metrics.renderedFrames + metrics.underrunSkippedFrames
            )
            assertEquals(metrics.renderedFrames, metrics.writtenFrames)
            assertEquals("Render deadline misses occurred", 0, metrics.deadlineMisses)
            assertEquals("Rendered events were dropped", 0, metrics.droppedEvents)
        } finally {
            engine.release()
        }
    }

    private fun logProgress(durationMinutes: Int, latch: CountDownLatch) {
        repeat(durationMinutes) { completedMinutes ->
            if (latch.await(1, TimeUnit.MINUTES)) return
            Log.i(TAG, "progressMinutes=${completedMinutes + 1}/$durationMinutes remainingEventFrames=${latch.count}")
        }
    }

    private companion object {
        const val TAG = "BeatClikrPolyStress"
        const val DURATION_ARGUMENT = "stressDurationMinutes"
        const val BEATS_ARGUMENT = "polyrhythmBeats"
        const val AGAINST_ARGUMENT = "polyrhythmAgainst"
        const val BPM_ARGUMENT = "polyrhythmBpm"
        const val DEFAULT_DURATION_MINUTES = 60
        const val MAX_DURATION_MINUTES = 60
        const val DEFAULT_BEATS = 5
        const val DEFAULT_AGAINST = 7
        const val DEFAULT_BPM = 120
        const val STOP_GRACE_SECONDS = 30L
    }
}
