package com.bfunkstudios.beatclikr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bfunkstudios.beatclikr.services.AudioOutputRoute
import com.bfunkstudios.beatclikr.services.MetronomeAudioEngine
import com.bfunkstudios.beatclikr.services.PlaybackInterruptionReason
import com.bfunkstudios.beatclikr.services.PlaybackSessionId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackRouteWiringTest {
    @Test
    fun deviceRemovalCallbackReachesSessionTaggedInterruptionObserver() {
        val engine = MetronomeAudioEngine(ApplicationProvider.getApplicationContext<Context>())
        val observed = mutableListOf<Pair<PlaybackSessionId, PlaybackInterruptionReason>>()
        val latch = CountDownLatch(1)
        try {
            engine.prepareRouteWiringForTesting()
            assertTrue(engine.awaitRouteWiringIdleForTesting())
            prepareActiveRoute(engine, PlaybackSessionId(41), AudioOutputRoute.BUILT_IN)
            engine.playbackInterruptionObserver = { sessionId, reason ->
                observed += sessionId to reason
                latch.countDown()
            }
            engine.audioDeviceCallbackForTesting().onAudioDevicesRemoved(emptyArray())

            assertTrue(latch.await(2, TimeUnit.SECONDS))
            assertEquals(
                PlaybackSessionId(41) to
                    PlaybackInterruptionReason.RouteUnavailable(AudioOutputRoute.BUILT_IN),
                observed.single()
            )
        } finally {
            engine.release()
        }
    }

    private fun prepareActiveRoute(
        engine: MetronomeAudioEngine,
        sessionId: PlaybackSessionId,
        route: AudioOutputRoute
    ) {
        engine.prepareActiveRouteForTesting(sessionId, route)
    }
}
