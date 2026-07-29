package com.bfunkstudios.beatclikr.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FrameEventTest {

    @Test
    fun tb002_standardEventCarriesFrameRoleSoundIdentityAndPosition() {
        val voice = EventVoice(
            role = MusicalEventRole.STANDARD,
            soundRole = SoundRole.BEAT,
            beatIdentity = BeatIdentity.ACCENT,
            position = CyclePosition(cycleIndex = 12, index = 3)
        )
        val event = FrameEvent(
            sequence = EventSequence(SessionID(1), 0),
            intendedFrame = 48_000,
            primary = voice
        )

        assertEquals(48_000L, event.intendedFrame)
        assertEquals(voice, event.primary)
        assertNull(event.secondary)
    }

    @Test
    fun mt018_coincidentPolyrhythmVoicesShareOneIntendedFrame() {
        val event = FrameEvent(
            sequence = EventSequence(SessionID(7), 12),
            intendedFrame = 96_000,
            primary = EventVoice(
                role = MusicalEventRole.POLYRHYTHM_BEAT,
                soundRole = SoundRole.BEAT,
                beatIdentity = BeatIdentity.BEAT,
                position = CyclePosition(4, 0)
            ),
            secondary = EventVoice(
                role = MusicalEventRole.POLYRHYTHM_RHYTHM,
                soundRole = SoundRole.RHYTHM,
                beatIdentity = BeatIdentity.BEAT,
                position = CyclePosition(4, 0)
            )
        )

        assertEquals(event.primary.position, event.secondary?.position)
        assertEquals(96_000L, event.intendedFrame)
    }

    @Test
    fun invalidFramesPositionsAndDuplicateVoicesFailImmediately() {
        val voice = EventVoice(
            role = MusicalEventRole.STANDARD,
            soundRole = SoundRole.RHYTHM,
            beatIdentity = BeatIdentity.SUBDIVISION,
            position = CyclePosition(0, 1)
        )

        assertThrows(IllegalArgumentException::class.java) {
            FrameEvent(EventSequence(SessionID(1), 0), -1, voice)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CyclePosition(-1, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CyclePosition(0, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrameEvent(
                EventSequence(SessionID(1), 0),
                0,
                voice,
                voice.copy(soundRole = SoundRole.BEAT)
            )
        }
    }
}
