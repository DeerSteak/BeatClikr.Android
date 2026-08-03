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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SecondaryOutputCoordinatorTest {
    private lateinit var prefs: IAppPreferences
    private lateinit var flashlight: IFlashlightService
    private lateinit var haptics: IHapticFeedbackService
    private lateinit var scheduler: RecordingScheduler
    private lateinit var coordinator: SecondaryOutputCoordinator
    private lateinit var transportState: MutableStateFlow<PlaybackTransportState>
    private lateinit var committedEvents: MutableSharedFlow<PlaybackCommittedEvent>
    private val sessionId = PlaybackSessionId(7)

    @Before
    fun setUp() {
        prefs = mockk(relaxed = true)
        flashlight = mockk(relaxed = true)
        haptics = mockk(relaxed = true)
        scheduler = RecordingScheduler()
        transportState = MutableStateFlow(standardPlaying())
        committedEvents = MutableSharedFlow(extraBufferCapacity = 4)
        val playback = mockk<PlaybackObservation>()
        every { playback.transportState } returns transportState
        every { playback.committedEvents } returns committedEvents
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

        applyTransportState(standardPlaying())
        assertNull(coordinator.committedEventDeliveryGap.value)
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

        applyTransportState(standardPlaying(session = PlaybackSessionId(8)))

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
    fun failedImmediateStopOffSchedulesOneDelayedRetry() {
        every { flashlight.turnFlashlightOff() } throws IllegalStateException("camera busy") andThen Unit

        coordinator.stopEffects()

        verify(exactly = 1) { flashlight.turnFlashlightOff() }
        assertEquals(250_000_000L, scheduler.tasks.single().delayNanos)
        scheduler.runNext()
        verify(exactly = 2) { flashlight.turnFlashlightOff() }
        assertTrue(scheduler.tasks.isEmpty())
        assertEquals("camera busy", coordinator.secondaryOutputFailure.value?.diagnostic)
    }

    @Test
    fun failedStopRetryPublishesTheLatestFailure() {
        every { flashlight.turnFlashlightOff() } throws IllegalStateException("first failure") andThenThrows
            IllegalStateException("retry failure")

        coordinator.stopEffects()
        scheduler.runNext()

        verify(exactly = 2) { flashlight.turnFlashlightOff() }
        assertEquals("retry failure", coordinator.secondaryOutputFailure.value?.diagnostic)
        assertTrue(scheduler.tasks.isEmpty())
    }

    @Test
    fun rejectedPulseOffScheduleAttemptsImmediateOff() {
        every { prefs.useFlashlight } returns true
        scheduler.rejectCalls += 2

        coordinator.applyCommittedEvent(rendered(roleIndex = 0))
        scheduler.runNext()

        verify { flashlight.turnFlashlightOn() }
        verify { flashlight.turnFlashlightOff() }
        assertEquals("scheduler rejected call 2", coordinator.secondaryOutputFailure.value?.diagnostic)
    }

    @Test
    fun rejectedFailsafeScheduleAttemptsImmediateOff() {
        every { prefs.useFlashlight } returns true
        scheduler.rejectCalls += 3

        coordinator.applyCommittedEvent(rendered(roleIndex = 0))
        scheduler.runNext()

        verify { flashlight.turnFlashlightOn() }
        verify { flashlight.turnFlashlightOff() }
        assertEquals("scheduler rejected call 3", coordinator.secondaryOutputFailure.value?.diagnostic)
    }

    @Test
    fun rejectedRetryMakesBoundedTerminalOffAttemptWithoutStoppingAudio() {
        every { prefs.useFlashlight } returns true
        every { flashlight.turnFlashlightOff() } throws IllegalStateException("camera busy")
        scheduler.rejectCalls += setOf(2, 3)
        val playing = transportState.value

        coordinator.applyCommittedEvent(rendered(roleIndex = 0))
        scheduler.runNext()

        verify { flashlight.turnFlashlightOn() }
        verify(exactly = 2) { flashlight.turnFlashlightOff() }
        assertEquals("camera busy", coordinator.secondaryOutputFailure.value?.diagnostic)
        assertSame(playing, transportState.value)
        assertEquals(0, scheduler.tasks.size)
    }

    @Test
    fun eventScheduleFailureDoesNotCancelLaterCollection() {
        every { prefs.useVibration } returns true
        scheduler.rejectCalls += 1
        coordinator.start()

        committedEvents.tryEmit(rendered(roleIndex = 0, sequence = 1))
        committedEvents.tryEmit(rendered(roleIndex = 0, sequence = 2))
        scheduler.runNext()

        assertEquals(SecondaryOutput.SCHEDULER, coordinator.secondaryOutputFailure.value?.output)
        verify(exactly = 1) { haptics.playBeatHaptic() }
        assertTrue(transportState.value is PlaybackTransportState.Playing)
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

    @Test
    fun replacementSessionSuppressesAlreadyScheduledPulse() {
        every { prefs.useVibration } returns true
        coordinator.applyCommittedEvent(rendered(roleIndex = 0))

        applyTransportState(standardPlaying(session = PlaybackSessionId(8)))
        scheduler.runNext()

        verify(exactly = 0) { haptics.playBeatHaptic() }
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
            CommittedPlaybackConfiguration.Standard(120f, 4, null, false, false),
        session: PlaybackSessionId = sessionId
    ) = PlaybackTransportState.Playing(
        PlaybackSessionContext(
            session,
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
        val rejectCalls = mutableSetOf<Int>()
        private var scheduleCalls = 0

        override fun schedule(delayNanos: Long, task: () -> Unit) {
            scheduleCalls += 1
            if (scheduleCalls in rejectCalls) {
                throw IllegalStateException("scheduler rejected call $scheduleCalls")
            }
            tasks += Task(delayNanos, task)
        }

        fun runNext() {
            tasks.removeAt(0).action()
        }
    }
}
