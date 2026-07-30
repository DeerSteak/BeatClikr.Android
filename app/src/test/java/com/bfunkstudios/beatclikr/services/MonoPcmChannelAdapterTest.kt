package com.bfunkstudios.beatclikr.services

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MonoPcmChannelAdapterTest {
    @Test
    fun monoOutputPreservesOneSamplePerFrame() {
        val adapter = MonoPcmChannelAdapter(maximumFrames = 3, channelCount = 1)

        val output = requireNotNull(adapter.adapt(shortArrayOf(10, 20, 30), 0, 3))

        assertArrayEquals(shortArrayOf(10, 20, 30), output)
    }

    @Test
    fun stereoOutputDuplicatesEachFrameWithoutChangingDuration() {
        val adapter = MonoPcmChannelAdapter(maximumFrames = 3, channelCount = 2)

        val output = requireNotNull(adapter.adapt(shortArrayOf(5, 10, 20, 30), 1, 3))

        assertArrayEquals(shortArrayOf(10, 10, 20, 20, 30, 30), output)
    }

    @Test
    fun adaptationReusesItsPublishedBuffer() {
        val adapter = MonoPcmChannelAdapter(maximumFrames = 2, channelCount = 2)

        val first = requireNotNull(adapter.adapt(shortArrayOf(1, 2), 0, 2))
        val second = requireNotNull(adapter.adapt(shortArrayOf(3, 4), 0, 2))

        assertSame(first, second)
        assertArrayEquals(shortArrayOf(3, 3, 4, 4), second)
    }

    @Test
    fun invalidRangesAreRejected() {
        val adapter = MonoPcmChannelAdapter(maximumFrames = 2, channelCount = 2)

        assertNull(adapter.adapt(shortArrayOf(1, 2), -1, 1))
        assertNull(adapter.adapt(shortArrayOf(1, 2), 1, 2))
        assertNull(adapter.adapt(shortArrayOf(1, 2, 3), 0, 3))
    }
}
