package com.bfunkstudios.beatclikr

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.bfunkstudios.beatclikr.data.SoundFile
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

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun mt012_mt015_mt018_representativeRatiosPreserveSharedOriginEventsAndIndices() {
        withEngine { engine ->
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

    private fun withEngine(block: (MetronomeAudioEngine) -> Unit) {
        val engine = MetronomeAudioEngine(context)
        try {
            engine.loadSounds(
                requireNotNull(SoundFile.CLICK_HI.resourceId),
                requireNotNull(SoundFile.CLICK_LO.resourceId)
            )
            engine.prewarm()
            Thread.sleep(PREWARM_SETTLE_MILLIS)
            block(engine)
        } finally {
            engine.release()
        }
    }

    private fun captureCycle(
        engine: MetronomeAudioEngine,
        fixture: EnginePolyrhythmFixture
    ): CycleCapture {
        val eventCount = fixture.events.size + 1
        val events = Collections.synchronizedList(mutableListOf<CapturedPolyrhythmEvent>())
        val latch = CountDownLatch(eventCount)
        engine.installPolyrhythmTestDelegate(object : PolyrhythmTestDelegate() {
            override fun polyrhythmBeatFired(
                beatFired: Boolean,
                rhythmFired: Boolean,
                beatIndex: Int,
                rhythmIndex: Int,
                stepTimeNanos: Long,
                beatDurationNanos: Long,
                rhythmDurationNanos: Long
            ) {
                if (latch.count == 0L) return
                events += CapturedPolyrhythmEvent(
                    identity = EventIdentity(beatFired, rhythmFired, beatIndex, rhythmIndex),
                    stepTimeNanos = stepTimeNanos,
                    beatDurationNanos = beatDurationNanos,
                    rhythmDurationNanos = rhythmDurationNanos
                )
                latch.countDown()
            }
        })

        engine.startPolyrhythm(TEST_BPM, fixture.beats, fixture.against)
        assertTrue("${fixture.beats}:${fixture.against} timed out", latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        awaitRenderedPrefix(engine, fixture, eventCount)
        engine.stopPolyrhythm()
        Thread.sleep(STOP_SETTLE_MILLIS)
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

    private fun awaitRenderedPrefix(
        engine: MetronomeAudioEngine,
        fixture: EnginePolyrhythmFixture,
        minimumEvents: Int
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_SECONDS * 1_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val metrics = requireNotNull(engine.getFrameAudioMetricsSnapshot())
            if (matchingPrefixLength(fixture, metrics, minimumEvents) != null) return
            Thread.sleep(10)
        }
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
        const val PREWARM_SETTLE_MILLIS = 150L
        const val STOP_SETTLE_MILLIS = 100L
        const val CYCLE_TOLERANCE_NANOS = 100_000L
    }
}
