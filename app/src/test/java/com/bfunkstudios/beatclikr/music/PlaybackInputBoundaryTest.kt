package com.bfunkstudios.beatclikr.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackInputBoundaryTest {

    @Test
    fun invalidExternalConfigurationBecomesTypedFailure() {
        val result = PlaybackInputBoundary.translate {
            AccentPattern.of(emptyList())
        } as PlaybackInputResult.Rejected
        val failure = result.failure as PlaybackInputFailure.InvalidDomainInput

        assertEquals("Accent pattern must not be empty", failure.diagnostic)
    }

    @Test
    fun validInputIsAcceptedForRenderHandoff() {
        val expected = ExactTempo.parse("137.5")
        val result = PlaybackInputBoundary.translate {
            expected
        } as PlaybackInputResult.Accepted

        assertEquals(expected, result.value)
    }

    @Test
    fun unexpectedProgrammingFailuresAreNotMisclassifiedAsInputRejections() {
        assertThrows(IllegalStateException::class.java) {
            PlaybackInputBoundary.translate {
                error("unexpected implementation failure")
            }
        }
    }
}
