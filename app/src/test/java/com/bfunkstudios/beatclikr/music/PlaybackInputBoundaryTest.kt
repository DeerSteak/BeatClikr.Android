package com.bfunkstudios.beatclikr.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun invalidCommandBatchCannotReachRenderHandoff() {
        var reachedRenderHandoff = false
        val result = PlaybackInputBoundary.translate {
            val boundaryResult = CommandBoundary.apply(
                current = PlaybackSnapshot(soundConfiguration()),
                commands = listOf(
                    SetGroove(
                        metadata(),
                        StandardSubdivision.SIXTEENTH
                    )
                ),
                restartOrigin = SessionOrigin(SessionID(1), 0)
            )
            reachedRenderHandoff = true
            boundaryResult
        }

        assertFalse(reachedRenderHandoff)
        assertEquals(
            "Command requires active standard playback",
            ((result as PlaybackInputResult.Rejected).failure as PlaybackInputFailure.InvalidDomainInput).diagnostic
        )
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

    private fun soundConfiguration() = SoundConfiguration(
        beatSound = SoundID("CLICK_HI"),
        rhythmSound = SoundID("CLICK_LOW"),
        soundBank = SoundBank.ACOUSTIC
    )

    private fun metadata() = CommandMetadata(
        sessionID = SessionID(1),
        commandSequence = CommandSequence(0),
        submissionTimestampNanos = 0
    )
}
