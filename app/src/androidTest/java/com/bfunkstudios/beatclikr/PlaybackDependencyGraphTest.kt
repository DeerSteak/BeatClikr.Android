package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.services.IAudioPlayerService
import com.bfunkstudios.beatclikr.services.PlaybackCoordinator
import com.bfunkstudios.beatclikr.services.PlaybackEnginePort
import com.bfunkstudios.beatclikr.services.PlaybackLifecycleObservation
import com.bfunkstudios.beatclikr.services.PlaybackObservation
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class PlaybackDependencyGraphTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var coordinator: PlaybackCoordinator
    @Inject lateinit var control: IAudioPlayerService
    @Inject lateinit var observation: PlaybackObservation
    @Inject lateinit var lifecycle: PlaybackLifecycleObservation
    @Inject lateinit var engine: PlaybackEnginePort

    @Test
    fun uiAndApplicationPortsResolveToCoordinatorInsteadOfEngineOwner() {
        hiltRule.inject()

        assertSame(coordinator, control)
        assertSame(coordinator, observation)
        assertSame(coordinator, lifecycle)
        assertNotSame(engine, control)
        assertNotSame(engine, observation)
    }
}
