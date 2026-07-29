package com.bfunkstudios.beatclikr.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSequenceTest {

    @Test
    fun mt011_mt012_sessionOriginStartsItsEventSequenceAtZero() {
        val origin = SessionOrigin(
            sessionID = SessionID(42),
            originFrame = 96_000
        )

        assertEquals(SessionID(42), origin.firstEventSequence().sessionID)
        assertEquals(0L, origin.firstEventSequence().index)
        assertEquals(96_000L, origin.originFrame)
    }

    @Test
    fun eventSequenceIsStrictlyMonotonicWithinOneSession() {
        var sequence = SessionOrigin(SessionID(7), 0).firstEventSequence()

        repeat(100_000) { expected ->
            assertEquals(expected.toLong(), sequence.index)
            sequence = sequence.next()
        }

        assertEquals(100_000L, sequence.index)
        assertEquals(SessionID(7), sequence.sessionID)
    }

    @Test
    fun separateSessionsCannotShareSequenceIdentity() {
        val first = SessionOrigin(SessionID(1), 0).firstEventSequence()
        val second = SessionOrigin(SessionID(2), 0).firstEventSequence()

        assertNotEquals(first, second)
        assertEquals(0L, first.index)
        assertEquals(0L, second.index)
    }

    @Test
    fun frameEventCarriesItsSessionAndMonotonicSequenceIdentity() {
        val sequence = EventSequence(SessionID(9), 14)
        val event = FrameEvent(
            sequence = sequence,
            intendedFrame = 48_000,
            primary = EventVoice(
                role = MusicalEventRole.STANDARD,
                soundRole = SoundRole.BEAT,
                beatIdentity = BeatIdentity.BEAT,
                position = CyclePosition(3, 0)
            )
        )

        assertEquals(SessionID(9), event.sequence.sessionID)
        assertEquals(14L, event.sequence.index)
    }

    @Test
    fun negativeAndExhaustedSessionValuesFailImmediately() {
        assertThrows(IllegalArgumentException::class.java) { SessionID(-1) }
        assertThrows(IllegalArgumentException::class.java) {
            SessionOrigin(SessionID(0), -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EventSequence(SessionID(0), -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EventSequence(SessionID(0), Long.MAX_VALUE).next()
        }
    }

    @Test
    fun nextSequenceAlwaysPreservesSessionIdentity() {
        var sequence = EventSequence(SessionID(Long.MAX_VALUE), 0)

        repeat(1_000) {
            val next = sequence.next()
            assertTrue(next.index > sequence.index)
            assertEquals(sequence.sessionID, next.sessionID)
            sequence = next
        }
    }
}
