package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.music.MusicalEventRole
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class SecondaryOutputCoordinatorTest {
    private lateinit var prefs: IAppPreferences
    private lateinit var flashlight: IFlashlightService
    private lateinit var haptics: IHapticFeedbackService
    private lateinit var scheduler: RecordingScheduler
    private lateinit var coordinator: SecondaryOutputCoordinator
    private lateinit var transportState: MutableStateFlow<PlaybackTransportState>
    private val sessionId = PlaybackSessionId(7)

    @Before
    fun setUp() {
        prefs = mockk(relaxed = true)
        flashlight = mockk(relaxed = true)
        haptics = mockk(relaxed = true)
        scheduler = RecordingScheduler()
        transportState = MutableStateFlow(standardPlaying())
        val playback = mockk<PlaybackObservation>()
        every { playback.transportState } returns transportState
        every { playback.committedEvents } returns MutableSharedFlow()
        coordinator = SecondaryOutputCoordinator(
            playback,
            prefs,
            flashlight,
            haptics,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            scheduler,
            nanoTime = { 1_000 }
        )
        coordinator.setVisible(true)
        applyTransportState(standardPlaying())
    }

    @Test
    fun committedBeatSchedulesHapticAndBoundedTorchPulse() {
        every { prefs.useVibration } returns true
        every { prefs.useFlashlight } returns true

        coordinator.applyCommittedEvent(rendered(roleIndex = 0, presentationNanos = 2_000))

        assertEquals(1_000, scheduler.tasks.single().delayNanos)
        scheduler.runNext()
        verify { haptics.playBeatHaptic() }
        verify { flashlight.turnFlashlightOn() }
        assertEquals(listOf(40_000_000L, 250_000_000L), scheduler.tasks.map { it.delayNanos })

        scheduler.runNext()
        scheduler.runNext()
        verify(exactly = 2) { flashlight.turnFlashlightOff() }
    }

    @Test
    fun inactiveAppSuppressesCommittedEffectsAndForcesOutputsOff() {
        coordinator.setVisible(false)
        coordinator.applyCommittedEvent(rendered(roleIndex = 0))

        assertEquals(0, scheduler.tasks.size)
        verify { haptics.cancel() }
        verify { flashlight.turnFlashlightOff() }
    }

    @Test
    fun stopInvalidatesPendingEventAndForcesOutputsOff() {
        every { prefs.useVibration } returns true
        coordinator.applyCommittedEvent(rendered(roleIndex = 0))

        applyTransportState(PlaybackTransportState.Idle)
        scheduler.runNext()

        verify(exactly = 0) { haptics.playBeatHaptic() }
        verify { haptics.cancel() }
        verify { flashlight.turnFlashlightOff() }
    }

    @Test
    fun oddMeterAccentUsesBeatOutputsFromCommittedRoleIndex() {
        every { prefs.useVibration } returns true
        applyTransportState(
            standardPlaying(
                CommittedPlaybackConfiguration.Standard(
                    120f,
                    5,
                    listOf(true, false, true, false, false),
                    false,
                    false
                )
            )
        )

        coordinator.applyCommittedEvent(rendered(roleIndex = 2))
        scheduler.runNext()

        verify { haptics.playBeatHaptic() }
    }

    @Test
    fun secondaryFailureIsPublishedWithoutChangingPlayback() {
        every { prefs.useVibration } returns true
        every { haptics.playBeatHaptic() } throws IllegalStateException("vibrator unavailable")
        val transportBeforeFailure = transportState.value

        coordinator.applyCommittedEvent(rendered(roleIndex = 0))
        scheduler.runNext()

        assertEquals(SecondaryOutput.HAPTIC, coordinator.secondaryOutputFailure.value?.output)
        assertEquals("vibrator unavailable", coordinator.secondaryOutputFailure.value?.diagnostic)
        assertSame(transportBeforeFailure, transportState.value)
    }

    @Test
    fun successfulSessionStartClearsRetainedFailure() {
        every { prefs.useVibration } returns true
        every { haptics.playBeatHaptic() } throws IllegalStateException("vibrator unavailable")
        coordinator.applyCommittedEvent(rendered(roleIndex = 0))
        scheduler.runNext()

        applyTransportState(standardPlaying())

        assertNull(coordinator.secondaryOutputFailure.value)
    }

    @Test
    fun torchOnFailurePublishesFailureAndAttemptsFailsafeOff() {
        every { prefs.useFlashlight } returns true
        every { flashlight.turnFlashlightOn() } throws IllegalStateException("camera busy")

        coordinator.applyCommittedEvent(rendered(roleIndex = 0))
        scheduler.runNext()

        assertEquals(SecondaryOutput.TORCH, coordinator.secondaryOutputFailure.value?.output)
        assertEquals("camera busy", coordinator.secondaryOutputFailure.value?.diagnostic)
        verify { flashlight.turnFlashlightOff() }
    }

    @Test
    fun failedImmediateStopOffSchedulesFreshFailsafe() {
        every { flashlight.turnFlashlightOff() } throws IllegalStateException("camera busy")

        coordinator.stopEffects()

        assertEquals(250_000_000L, scheduler.tasks.single().delayNanos)
        assertEquals("camera busy", coordinator.secondaryOutputFailure.value?.diagnostic)
    }

    @Test
    fun disabledOutputsLeaveFailureClear() {
        coordinator.applyCommittedEvent(rendered(roleIndex = 0))
        scheduler.runNext()

        assertNull(coordinator.secondaryOutputFailure.value)
        verify(exactly = 0) { haptics.playBeatHaptic() }
        verify(exactly = 0) { flashlight.turnFlashlightOn() }
    }

    @Test
    fun deliveryGapStopsEffectsAndSkipsCatchUpEvent() {
        every { prefs.useVibration } returns true
        coordinator.applyCommittedEvent(rendered(roleIndex = 0, sequence = 1))
        scheduler.runNext()

        coordinator.applyCommittedEvent(rendered(roleIndex = 1, sequence = 4))

        assertEquals(
            CommittedEventDeliveryGap(expectedSequence = 2, observedSequence = 4),
            coordinator.committedEventDeliveryGap.value
        )
        assertEquals(0, scheduler.tasks.size)
        verify(exactly = 1) { haptics.playBeatHaptic() }
        verify { haptics.cancel() }
        verify { flashlight.turnFlashlightOff() }
    }

    private fun rendered(
        roleIndex: Int,
        presentationNanos: Long? = null,
        sequence: Long = 1
    ) = PlaybackCommittedEvent.Rendered(
        sequence,
        sessionId,
        1,
        MusicalEventRole.STANDARD,
        0,
        false,
        presentationNanos?.let {
            EventPresentation.Correlated(it, AudioFrameCorrelation(0, 0, 0))
        } ?: EventPresentation.Unavailable,
        roleIndex
    )

    private fun applyTransportState(state: PlaybackTransportState) {
        transportState.value = state
        coordinator.applyTransportState(state)
    }

    private fun standardPlaying(
        configuration: CommittedPlaybackConfiguration.Standard =
            CommittedPlaybackConfiguration.Standard(120f, 4, null, false, false)
    ) = PlaybackTransportState.Playing(
        PlaybackSessionContext(
            sessionId,
            PlaybackMode.STANDARD,
            configuration,
            audibleSounds = ActiveSoundConfiguration(
                SoundBank.ACOUSTIC,
                SoundFile.CLICK_HI,
                SoundFile.CLICK_LO
            ),
            route = AudioOutputRoute.BUILT_IN,
            backend = AudioBackendType.AUDIO_TRACK,
            startOrigin = PlaybackStartOrigin.USER
        )
    )

    private class RecordingScheduler : SecondaryOutputScheduler {
        data class Task(val delayNanos: Long, val action: () -> Unit)

        val tasks = mutableListOf<Task>()

        override fun schedule(delayNanos: Long, task: () -> Unit) {
            tasks += Task(delayNanos, task)
        }

        fun runNext() {
            tasks.removeAt(0).action()
        }
    }
}
