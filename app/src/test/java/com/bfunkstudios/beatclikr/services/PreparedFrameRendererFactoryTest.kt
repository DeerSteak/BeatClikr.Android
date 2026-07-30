package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.music.ExactTempo
import com.bfunkstudios.beatclikr.music.FrameRange
import com.bfunkstudios.beatclikr.music.SoundRole
import com.bfunkstudios.beatclikr.music.PolyrhythmConfiguration
import com.bfunkstudios.beatclikr.music.SessionID
import com.bfunkstudios.beatclikr.music.SessionOrigin
import com.bfunkstudios.beatclikr.music.StandardMetronomeConfiguration
import com.bfunkstudios.beatclikr.music.StandardSubdivision
import com.bfunkstudios.beatclikr.music.StandardTiming
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PreparedFrameRendererFactoryTest {
    @Test
    fun standardPublicationUsesObtainedSampleRateAndPreparedWaveforms() {
        val publication = StandardPreparedFrameRendererFactory(
            StandardMetronomeConfiguration(
                ExactTempo.of(120),
                StandardTiming.Regular(StandardSubdivision.EIGHTH)
            ),
            SessionOrigin(SessionID(1), 0),
            preparedSounds()
        ).create(PROPERTIES)
        val renderer = publication.renderer
        renderer.prepare(2)
        renderer.reset()
        val output = ShortArray(2)

        assertEquals(
            FrameRenderResult.COMPLETE,
            renderer.render(11_999, output, output.size)
        )

        assertArrayEquals(shortArrayOf(0, 3), output)
        assertNotNull(publication.recovery)
        assertEquals(0, publication.firstOutputFrame)
    }

    @Test
    fun polyrhythmPublicationBindsCoincidentPreparedSoundsOnce() {
        val publication = PolyrhythmPreparedFrameRendererFactory(
            PolyrhythmConfiguration(ExactTempo.of(120), beats = 3, against = 2),
            SessionOrigin(SessionID(2), 0),
            preparedSounds()
        ).create(PROPERTIES)
        val renderer = publication.renderer
        renderer.prepare(1)
        val output = ShortArray(1)

        assertEquals(FrameRenderResult.COMPLETE, renderer.render(0, output, 1))

        assertArrayEquals(shortArrayOf(10), output)
        assertNotNull(publication.recovery)
    }

    @Test
    fun polyrhythmUpdateWaitsForSharedCycleBoundaryWithoutResettingIdentity() {
        val publication = PolyrhythmPreparedFrameRendererFactory(
            PolyrhythmConfiguration(ExactTempo.of(120), beats = 3, against = 2),
            SessionOrigin(SessionID(7), 0),
            preparedSounds()
        ).create(PROPERTIES)
        val replacement = requireNotNull(publication.polyrhythmUpdater).update(
            PolyrhythmConfiguration(ExactTempo.of(180), beats = 5, against = 7),
            firstUnprocessedFrame = 1
        )
        val event = requireNotNull(replacement)
            .eventsIn(FrameRange(1, 48_001))
            .single()

        assertEquals(48_000, event.intendedFrame)
        assertEquals(4, event.sequence.index)
        assertEquals(1, event.primary.position.cycleIndex)
        assertNotNull(event.secondary)
    }

    @Test
    fun publicationCarriesNonzeroSessionOriginIntoStreamStart() {
        val publication = StandardPreparedFrameRendererFactory(
            StandardMetronomeConfiguration(
                ExactTempo.of(120),
                StandardTiming.Regular(StandardSubdivision.QUARTER)
            ),
            SessionOrigin(SessionID(3), 321),
            preparedSounds()
        ).create(PROPERTIES)

        assertEquals(321, publication.firstOutputFrame)
    }

    @Test
    fun delayedStandardPublicationStartsWithSilenceBeforeFirstBeat() {
        val publication = StandardPreparedFrameRendererFactory(
            StandardMetronomeConfiguration(
                ExactTempo.of(120),
                StandardTiming.Regular(StandardSubdivision.QUARTER)
            ),
            SessionOrigin(SessionID(4), 100),
            preparedSounds(),
            startDelayMillis = 10
        ).create(PROPERTIES)
        val renderer = publication.renderer
        renderer.prepare(2)
        val output = ShortArray(2)

        assertEquals(FrameRenderResult.COMPLETE, renderer.render(579, output, 2))

        assertEquals(100, publication.firstOutputFrame)
        assertArrayEquals(shortArrayOf(0, 7), output)
    }

    @Test
    fun standardUpdateKeepsNextOldTempoBoundaryAndPatternPhase() {
        val publication = StandardPreparedFrameRendererFactory(
            StandardMetronomeConfiguration(
                ExactTempo.of(120),
                StandardTiming.Regular(StandardSubdivision.EIGHTH)
            ),
            SessionOrigin(SessionID(5), 0),
            preparedSounds()
        ).create(PROPERTIES)
        val replacement = requireNotNull(publication.standardUpdater).update(
            StandardMetronomeConfiguration(
                ExactTempo.of(240),
                StandardTiming.Regular(StandardSubdivision.EIGHTH)
            ),
            firstUnprocessedFrame = 1
        )
        val events = requireNotNull(replacement).eventsIn(FrameRange(1, 18_001)).toList()

        assertEquals(listOf(12_000L, 18_000L), events.map { it.intendedFrame })
        assertEquals(
            listOf(SoundRole.RHYTHM, SoundRole.BEAT),
            events.map { it.primary.soundRole }
        )
        assertEquals(listOf(1L, 2L), events.map { it.sequence.index })
    }

    @Test
    fun laterUpdateSupersedesPendingUpdateAtTheSameBoundary() {
        val publication = StandardPreparedFrameRendererFactory(
            StandardMetronomeConfiguration(
                ExactTempo.of(120),
                StandardTiming.Regular(StandardSubdivision.EIGHTH)
            ),
            SessionOrigin(SessionID(6), 0),
            preparedSounds()
        ).create(PROPERTIES)
        val updater = requireNotNull(publication.standardUpdater)
        updater.update(
            StandardMetronomeConfiguration(
                ExactTempo.of(240),
                StandardTiming.Regular(StandardSubdivision.EIGHTH)
            ),
            firstUnprocessedFrame = 1
        )
        val replacement = updater.update(
            StandardMetronomeConfiguration(
                ExactTempo.of(180),
                StandardTiming.Regular(StandardSubdivision.EIGHTH)
            ),
            firstUnprocessedFrame = 1
        )
        val events = requireNotNull(replacement).eventsIn(FrameRange(1, 20_001)).toList()

        assertEquals(listOf(12_000L, 20_000L), events.map { it.intendedFrame })
        assertEquals(listOf(1L, 2L), events.map { it.sequence.index })
    }

    private fun preparedSounds(): ActivePreparedSounds =
        ActivePreparedSounds(
            bank = SoundBank.ACOUSTIC,
            beatSound = SoundFile.CLICK_HI,
            rhythmSound = SoundFile.CLICK_LO,
            beat = shortArrayOf(7),
            rhythm = shortArrayOf(3)
        )

    private companion object {
        val PROPERTIES = AudioBackendStreamProperties(
            sampleRate = 48_000,
            channelCount = 2,
            burstFrames = 192,
            bufferFrames = 384,
            performanceMode = AudioBackendPerformanceMode.LOW_LATENCY
        )
    }
}
