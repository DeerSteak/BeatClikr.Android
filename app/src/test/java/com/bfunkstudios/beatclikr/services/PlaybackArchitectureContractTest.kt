package com.bfunkstudios.beatclikr.services

import java.lang.reflect.Modifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackArchitectureContractTest {
    @Test
    fun enginePortHasNoParallelTimingDelegateSurface() {
        assertFalse(
            PlaybackEnginePort::class.java.methods.any {
                it.name.contains("delegate", ignoreCase = true)
            }
        )
        assertTrue(PlaybackEnginePort::class.java.isAssignableFrom(MetronomeAudioEngine::class.java))
    }

    @Test
    fun activeRendererWaveformsCannotBeRebound() {
        listOf("beat", "rhythm").forEach { name ->
            val field = ActivePreparedSounds::class.java.getDeclaredField(name)
            assertTrue("$name waveform binding must remain final", Modifier.isFinal(field.modifiers))
        }
    }
}
