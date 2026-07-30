package com.bfunkstudios.beatclikr.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveOutputRouteTrackerTest {
    @Test
    fun initialResolutionAndDuplicateSignalsDoNotInterrupt() {
        val tracker = ActiveOutputRouteTracker()

        assertNull(tracker.observe(AudioOutputRoute.BUILT_IN))
        assertNull(tracker.observe(AudioOutputRoute.BUILT_IN))
    }

    @Test
    fun duplicateDetectorsEmitOneRouteChange() {
        val tracker = ActiveOutputRouteTracker()
        tracker.begin(AudioOutputRoute.BUILT_IN)

        assertEquals(
            PlaybackInterruptionReason.RouteChanged(
                AudioOutputRoute.BUILT_IN,
                AudioOutputRoute.BLUETOOTH
            ),
            tracker.observe(AudioOutputRoute.BLUETOOTH)
        )
        assertNull(tracker.observe(AudioOutputRoute.BLUETOOTH))
    }

    @Test
    fun transientUnknownDoesNotHideTheNextRealRouteChange() {
        val tracker = ActiveOutputRouteTracker()
        tracker.begin(AudioOutputRoute.BUILT_IN)

        assertNull(tracker.observe(AudioOutputRoute.UNKNOWN))
        assertEquals(
            PlaybackInterruptionReason.RouteChanged(
                AudioOutputRoute.BUILT_IN,
                AudioOutputRoute.WIRED
            ),
            tracker.observe(AudioOutputRoute.WIRED)
        )
    }

    @Test
    fun clearPreventsStoppedSessionFromEmitting() {
        val tracker = ActiveOutputRouteTracker()
        tracker.begin(AudioOutputRoute.BUILT_IN)
        tracker.clear()

        assertNull(tracker.observe(AudioOutputRoute.WIRED))
    }
}
