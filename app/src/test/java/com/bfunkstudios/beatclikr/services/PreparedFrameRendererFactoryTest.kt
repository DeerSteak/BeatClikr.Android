package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.music.ExactTempo
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
