package com.bfunkstudios.beatclikr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.bfunkstudios.beatclikr.music.MusicalEventRole
import com.bfunkstudios.beatclikr.services.FrameAudioMetricsSnapshot
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngine
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class PolyrhythmContractInstrumentedTest {

    @Test
    fun mt012_mt015_mt018_representativeRatiosPreserveSharedOriginEventsAndIndices() {
        withPreparedAudioEngine(prewarm = true) { engine ->
            EnginePolyrhythmFixtures.representativeRatios.forEach { fixture ->
                val capture = captureCycle(engine, fixture)
                val after = requireNotNull(engine.getFrameAudioMetricsSnapshot())
                val expected = fixture.events + fixture.events.first().copy(stepIndex = fixture.gridSize)

                assertEquals("${fixture.beats}:${fixture.against} events", expected.map { it.identity }, capture.events.map { it.identity })
                assertCycleTiming(fixture, capture.events)
                assertSoundCounts(fixture, after, expected.size)
            }
        }
    }

    private fun captureCycle(
        engine: MetronomeAudioEngine,
        fixture: EnginePolyrhythmFixture
    ): CycleCapture {
        val eventCount = fixture.events.size + 1
        val events = Collections.synchronizedList(mutableListOf<CapturedPolyrhythmEvent>())
        val latch = CountDownLatch(eventCount)
        var beatIndex = 0
        var rhythmIndex = 0
        val session = RenderedEventTestSession.polyrhythm(
            engine, TEST_BPM, fixture.beats, fixture.against
        ) { records, sampleRate ->
            records.groupBy { it.intendedFrame }.values.forEach { simultaneous ->
                val beat = simultaneous.firstOrNull { it.role == MusicalEventRole.POLYRHYTHM_BEAT }
                val rhythm = simultaneous.firstOrNull { it.role == MusicalEventRole.POLYRHYTHM_RHYTHM }
                beat?.let { beatIndex = it.roleIndex }
                rhythm?.let { rhythmIndex = it.roleIndex }
                if (latch.count > 0L) {
                    events += CapturedPolyrhythmEvent(
                        EventIdentity(beat != null, rhythm != null, beatIndex, rhythmIndex),
                        simultaneous.first().intendedFrame * 1_000_000_000L / sampleRate,
                        (60_000_000_000.0 / TEST_BPM).toLong(),
                        (60_000_000_000.0 * fixture.against /
                            (TEST_BPM * fixture.beats)).toLong()
                    )
                    latch.countDown()
                }
            }
        }
        assertTrue("${fixture.beats}:${fixture.against} timed out", latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        awaitFrameAudioMetrics(engine, TIMEOUT_SECONDS * 1_000) {
            matchingPrefixLength(fixture, it, eventCount) != null
        }
        session.close()
        return CycleCapture(synchronized(events) { events.toList() })
    }

    private fun assertCycleTiming(
        fixture: EnginePolyrhythmFixture,
        events: List<CapturedPolyrhythmEvent>
    ) {
        val expectedBeatDuration = (60_000_000_000.0 / TEST_BPM).toLong()
        val expectedRhythmDuration = (fixture.against * 60_000_000_000.0 / TEST_BPM / fixture.beats).toLong()
        val expectedCycleDuration = fixture.against * 60_000_000_000.0 / TEST_BPM
        val actualCycleDuration = events.last().stepTimeNanos - events.first().stepTimeNanos

        assertTrue(
            "${fixture.beats}:${fixture.against} cycle",
            abs(actualCycleDuration - expectedCycleDuration) <= CYCLE_TOLERANCE_NANOS + fixture.gridSize
        )
        events.forEach {
            assertEquals(expectedBeatDuration, it.beatDurationNanos)
            assertEquals(expectedRhythmDuration, it.rhythmDurationNanos)
        }
    }

    private fun assertSoundCounts(
        fixture: EnginePolyrhythmFixture,
        after: FrameAudioMetricsSnapshot,
        minimumEvents: Int
    ) {
        assertTrue(
            "${fixture.beats}:${fixture.against} sound roles",
            matchingPrefixLength(fixture, after, minimumEvents) != null
        )
    }

    private fun matchingPrefixLength(
        fixture: EnginePolyrhythmFixture,
        metrics: FrameAudioMetricsSnapshot,
        minimumEvents: Int
    ): Int? {
        val maximumEvents = minimumEvents + fixture.events.size * 2
        return (minimumEvents..maximumEvents).firstOrNull { count ->
            val events = List(count) { fixture.events[it % fixture.events.size] }
            events.count { it.beatFired }.toLong() == metrics.queuedBeatClicks &&
                events.count { it.rhythmFired }.toLong() == metrics.queuedRhythmClicks
        }
    }

    private val EnginePolyrhythmEvent.identity: EventIdentity
        get() = EventIdentity(beatFired, rhythmFired, beatIndex, rhythmIndex)

    private data class EventIdentity(
        val beatFired: Boolean,
        val rhythmFired: Boolean,
        val beatIndex: Int,
        val rhythmIndex: Int
    )

    private data class CapturedPolyrhythmEvent(
        val identity: EventIdentity,
        val stepTimeNanos: Long,
        val beatDurationNanos: Long,
        val rhythmDurationNanos: Long
    )

    private data class CycleCapture(
        val events: List<CapturedPolyrhythmEvent>
    )

    private companion object {
        const val TEST_BPM = 240f
        const val TIMEOUT_SECONDS = 6L
        const val CYCLE_TOLERANCE_NANOS = 100_000L
    }
}
