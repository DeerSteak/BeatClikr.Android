package com.bfunkstudios.beatclikr

import androidx.lifecycle.LifecycleOwner
import com.bfunkstudios.beatclikr.services.SecondaryOutputCoordinator
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class SecondaryOutputProcessLifecycleObserverTest {
    private val outputs = mockk<SecondaryOutputCoordinator>(relaxed = true)
    private val owner = mockk<LifecycleOwner>(relaxed = true)

    @Test
    fun startedProcessEnablesSecondaryOutputs() {
        val observer = SecondaryOutputProcessLifecycleObserver(outputs) {}

        observer.onStart(owner)

        verify { outputs.setVisible(true) }
    }

    @Test
    fun stoppedProcessDisablesOutputsAndReleasesResources() {
        var inactiveCalls = 0
        val observer = SecondaryOutputProcessLifecycleObserver(outputs) { inactiveCalls += 1 }

        observer.onStop(owner)

        verify { outputs.setVisible(false) }
        assertEquals(1, inactiveCalls)
    }

    @Test
    fun startedProcessIgnoresActivityOnlyLifecycleChanges() {
        var inactiveCalls = 0
        val observer = SecondaryOutputProcessLifecycleObserver(outputs) { inactiveCalls += 1 }
        observer.onStart(owner)

        // Configuration changes, overlays, and multi-window do not emit process ON_STOP.
        verify(exactly = 0) { outputs.setVisible(false) }
        assertEquals(0, inactiveCalls)
    }
}
