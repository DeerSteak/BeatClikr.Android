package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.IAppPreferences
import com.bfunkstudios.beatclikr.music.MusicalEventRole
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SecondaryOutput {
    HAPTIC,
    TORCH,
    SCHEDULER
}

data class SecondaryOutputFailure(
    val output: SecondaryOutput,
    val operation: String,
    val diagnostic: String
)

interface SecondaryOutputObservation {
    val secondaryOutputFailure: StateFlow<SecondaryOutputFailure?>
    val committedEventDeliveryGap: StateFlow<CommittedEventDeliveryGap?>
}

fun interface SecondaryOutputScheduler {
    fun schedule(delayNanos: Long, task: () -> Unit)
}

class ExecutorSecondaryOutputScheduler(
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "SecondaryOutput").apply { isDaemon = true }
        }
) : SecondaryOutputScheduler {
    override fun schedule(delayNanos: Long, task: () -> Unit) {
        executor.schedule(task, delayNanos.coerceAtLeast(0), TimeUnit.NANOSECONDS)
    }
}

class SecondaryOutputCoordinator(
    private val playback: PlaybackObservation,
    private val prefs: IAppPreferences,
    private val flashlight: IFlashlightService,
    private val haptics: IHapticFeedbackService,
    private val scope: CoroutineScope,
    private val scheduler: SecondaryOutputScheduler = ExecutorSecondaryOutputScheduler(),
    private val nanoTime: () -> Long = System::nanoTime
) : SecondaryOutputObservation {
    private val started = AtomicBoolean(false)
    private val pulseGeneration = AtomicLong(0)
    private val mutableFailure = MutableStateFlow<SecondaryOutputFailure?>(null)
    private val mutableDeliveryGap = MutableStateFlow<CommittedEventDeliveryGap?>(null)
    private val committedEventCursor = CommittedEventDeliveryCursor(
        playback.committedEvents.replayCache.lastOrNull()?.sequence ?: 0L
    )

    @Volatile private var visible = false
    override val secondaryOutputFailure: StateFlow<SecondaryOutputFailure?> = mutableFailure
    override val committedEventDeliveryGap: StateFlow<CommittedEventDeliveryGap?> =
        mutableDeliveryGap

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            playback.transportState.collect { state ->
                runSecondaryCollection("transport collection") { applyTransportState(state) }
            }
        }
        scope.launch {
            playback.committedEvents.collect { event ->
                runSecondaryCollection("event collection") { applyCommittedEvent(event) }
            }
        }
    }

    fun setVisible(isVisible: Boolean) {
        visible = isVisible
        if (!isVisible) stopEffects()
    }

    fun stopEffects() {
        val generation = pulseGeneration.incrementAndGet()
        runOutput(SecondaryOutput.HAPTIC, "cancel") { haptics.cancel() }
        if (!runOutput(SecondaryOutput.TORCH, "off") { flashlight.turnFlashlightOff() }) {
            scheduleTerminalTorchOff(generation, "stop failsafe off")
        }
    }

    internal fun applyTransportState(state: PlaybackTransportState) {
        if (state is PlaybackTransportState.Playing) {
            mutableFailure.value = null
            mutableDeliveryGap.value = null
        } else {
            stopEffects()
        }
    }

    internal fun applyCommittedEvent(event: PlaybackCommittedEvent) {
        when (val delivery = committedEventCursor.accept(event)) {
            CommittedEventDeliveryResult.Accepted -> Unit
            CommittedEventDeliveryResult.Duplicate -> return
            is CommittedEventDeliveryResult.Gap -> {
                mutableDeliveryGap.value = delivery.detail
                stopEffects()
                return
            }
        }
        val rendered = event as? PlaybackCommittedEvent.Rendered ?: return
        val playing = playback.transportState.value as? PlaybackTransportState.Playing ?: return
        if (!visible || rendered.sessionId != playing.context.sessionId) return
        val delay = (rendered.presentation as? EventPresentation.Correlated)
            ?.presentationNanoTime
            ?.minus(nanoTime())
            ?.coerceAtLeast(0)
            ?: 0
        scheduleOutput(SecondaryOutput.SCHEDULER, "event dispatch", delay) {
            dispatch(rendered)
        }
    }

    private fun dispatch(event: PlaybackCommittedEvent.Rendered) {
        val playing = playback.transportState.value as? PlaybackTransportState.Playing ?: return
        if (!visible || event.sessionId != playing.context.sessionId) return
        val isBeat = when (event.role) {
            MusicalEventRole.STANDARD -> {
                val configuration =
                    playing.context.configuration as? CommittedPlaybackConfiguration.Standard
                configuration?.accentPattern?.getOrNull(event.roleIndex) ?: (event.roleIndex == 0)
            }
            MusicalEventRole.POLYRHYTHM_BEAT -> true
            MusicalEventRole.POLYRHYTHM_RHYTHM -> false
        }
        if (prefs.useVibration) {
            runOutput(SecondaryOutput.HAPTIC, if (isBeat) "beat" else "rhythm") {
                if (isBeat) haptics.playBeatHaptic() else haptics.playRhythmHaptic()
            }
        }
        if (isBeat && prefs.useFlashlight) pulseTorch()
    }

    private fun pulseTorch() {
        val generation = pulseGeneration.incrementAndGet()
        val enabled = runOutput(SecondaryOutput.TORCH, "on") {
            flashlight.turnFlashlightOn()
        }
        if (!enabled) {
            recoverTorchOff(generation, "on failure recovery")
            return
        }
        if (!scheduleTorchOff(generation, TORCH_PULSE_NANOS, "pulse off")) {
            recoverTorchOff(generation, "pulse schedule recovery")
            return
        }
        if (!scheduleTorchOff(generation, TORCH_FAILSAFE_NANOS, "failsafe off")) {
            recoverTorchOff(generation, "failsafe schedule recovery")
        }
    }

    private fun scheduleTorchOff(
        generation: Long,
        delayNanos: Long,
        operation: String
    ): Boolean = scheduleOutput(SecondaryOutput.TORCH, "$operation schedule", delayNanos) {
        if (pulseGeneration.get() == generation) {
            runOutput(SecondaryOutput.TORCH, operation) { flashlight.turnFlashlightOff() }
        }
    }

    private fun recoverTorchOff(generation: Long, operation: String) {
        if (runOutput(SecondaryOutput.TORCH, operation) { flashlight.turnFlashlightOff() }) return
        scheduleTerminalTorchOff(generation, "$operation retry")
    }

    private fun scheduleTerminalTorchOff(generation: Long, operation: String) {
        val scheduled = scheduleOutput(
            SecondaryOutput.TORCH,
            "$operation schedule",
            TORCH_FAILSAFE_NANOS
        ) {
            if (pulseGeneration.get() == generation &&
                !runOutput(SecondaryOutput.TORCH, operation) {
                    flashlight.turnFlashlightOff()
                }) {
                runOutput(SecondaryOutput.TORCH, "$operation terminal") {
                    flashlight.turnFlashlightOff()
                }
            }
        }
        if (!scheduled) {
            runOutput(SecondaryOutput.TORCH, "$operation terminal") {
                flashlight.turnFlashlightOff()
            }
        }
    }

    private fun scheduleOutput(
        output: SecondaryOutput,
        operation: String,
        delayNanos: Long,
        task: () -> Unit
    ): Boolean = try {
        scheduler.schedule(delayNanos) {
            try {
                task()
            } catch (failure: Exception) {
                publishFailure(output, operation, failure)
            }
        }
        true
    } catch (failure: Exception) {
        publishFailure(output, operation, failure)
        false
    }

    private fun runSecondaryCollection(operation: String, action: () -> Unit) {
        try {
            action()
        } catch (failure: Exception) {
            publishFailure(SecondaryOutput.SCHEDULER, operation, failure)
        }
    }

    private fun publishFailure(
        output: SecondaryOutput,
        operation: String,
        failure: Exception
    ) {
        mutableFailure.value = SecondaryOutputFailure(
            output,
            operation,
            failure.message ?: failure::class.java.simpleName
        )
    }

    private fun runOutput(
        output: SecondaryOutput,
        operation: String,
        action: () -> Unit
    ): Boolean = try {
        action()
        true
    } catch (failure: Exception) {
        publishFailure(output, operation, failure)
        false
    }

    private companion object {
        const val TORCH_PULSE_NANOS = 40_000_000L
        const val TORCH_FAILSAFE_NANOS = 250_000_000L
    }
}
