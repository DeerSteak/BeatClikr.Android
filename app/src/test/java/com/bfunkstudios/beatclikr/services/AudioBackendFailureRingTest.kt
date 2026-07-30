package com.bfunkstudios.beatclikr.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBackendFailureRingTest {
    @Test
    fun overwritesOldestFailureAtFixedCapacity() {
        val ring = AudioBackendFailureRing(2)
        val first = failure(AudioBackendOperation.OPEN)
        val second = failure(AudioBackendOperation.START)
        val third = failure(AudioBackendOperation.RENDER)

        ring.record(first)
        ring.record(second)
        ring.record(third)

        assertEquals(listOf(second, third), ring.snapshot())
    }

    @Test
    fun resetStartsANewFailureHistory() {
        val ring = AudioBackendFailureRing(2)
        ring.record(failure(AudioBackendOperation.OPEN))

        ring.reset()

        assertTrue(ring.snapshot().isEmpty())
    }

    private fun failure(operation: AudioBackendOperation) =
        AudioBackendFailure(operation, AudioBackendFailureCode.INTERNAL_ERROR)
}
