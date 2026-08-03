package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.music.PlaybackInputFailure
import com.bfunkstudios.beatclikr.music.PlaybackInputResult
import com.bfunkstudios.beatclikr.music.SessionID
import com.bfunkstudios.beatclikr.music.SessionOrigin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePlaybackPublicationBoundaryTest {
    @Test
    fun acceptsRegularAdditiveAndPolyrhythmPublications() {
        val regular = FramePlaybackPublicationBoundary.standard(
            standard(120f, 4, null, true).render, ORIGIN, SOUNDS
        )
        val additive = FramePlaybackPublicationBoundary.standard(
            standard(137.5f, 2, listOf(true, false, true), false).render, ORIGIN, SOUNDS
        )
        val polyrhythm = FramePlaybackPublicationBoundary.polyrhythm(
            polyrhythm(120f, 3, 2).render, ORIGIN, SOUNDS
        )

        assertTrue(regular is FramePublicationResult.Ready)
        assertTrue(additive is FramePublicationResult.Ready)
        assertTrue(polyrhythm is FramePublicationResult.Ready)
    }

    @Test
    fun rejectsMissingSoundsAndInvalidInputsWithoutThrowing() {
        val missing = FramePlaybackPublicationBoundary.standard(
            standard(120f, 1, null, false).render, ORIGIN, null
        )
        val subdivision = FramePlaybackPublicationBoundary.standardConfiguration(
            120f, 5, null, false, false
        )
        val accents = FramePlaybackPublicationBoundary.standardConfiguration(
            120f, 2, listOf(false, true), false, false
        )
        val ratio = FramePlaybackPublicationBoundary.polyrhythmConfiguration(
            120f, 16, 2, false
        )

        assertRejected(missing, FramePublicationFailureCode.SOUNDS_UNAVAILABLE)
        listOf(subdivision, accents, ratio).forEach {
            assertTrue(
                (it as PlaybackInputResult.Rejected).failure is
                    PlaybackInputFailure.InvalidDomainInput
            )
        }
    }

    @Test
    fun rampDerivedFractionalTempoRetainsExactFramePeriod() {
        val rampDerivedBpm = 137.3f + 5f
        assertEquals("142.3", rampDerivedBpm.toString())
        val result = FramePlaybackPublicationBoundary.standard(
            standard(rampDerivedBpm, 1, null, false).render, ORIGIN, SOUNDS
        ) as FramePublicationResult.Ready
        val renderer = requireNotNull(result.factory.create(PROPERTIES)).renderer
        renderer.prepare(2)
        renderer.reset()
        val output = ShortArray(2)

        renderer.render(20_238, output, 2)

        assertArrayEquals(shortArrayOf(0, 1), output)
    }

    private fun assertRejected(result: FramePublicationResult, code: FramePublicationFailureCode) {
        assertEquals(code, (result as FramePublicationResult.Rejected).code)
    }

    private fun standard(
        bpm: Float,
        subdivisions: Int,
        accents: List<Boolean>?,
        alternateSixteenth: Boolean
    ) = (FramePlaybackPublicationBoundary.standardConfiguration(
        bpm, subdivisions, accents, alternateSixteenth, false
    ) as PlaybackInputResult.Accepted).value

    private fun polyrhythm(bpm: Float, beats: Int, against: Int) =
        (FramePlaybackPublicationBoundary.polyrhythmConfiguration(
            bpm, beats, against, false
        ) as PlaybackInputResult.Accepted).value

    private companion object {
        val PROPERTIES = AudioBackendStreamProperties(48_000, 1, 192, 384)
        val ORIGIN = SessionOrigin(SessionID(1), 0)
        val SOUNDS = ActivePreparedSounds(
            SoundBank.ACOUSTIC,
            SoundFile.CLICK_HI,
            SoundFile.CLICK_LO,
            shortArrayOf(1),
            shortArrayOf(1)
        )
    }
}
