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
    TORCH
}

data class SecondaryOutputFailure(
    val output: SecondaryOutput,
    val operation: String,
    val diagnostic: String
)

interface SecondaryOutputObservation {
    val secondaryOutputFailure: StateFlow<SecondaryOutputFailure?>
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

    @Volatile private var visible = false
    override val secondaryOutputFailure: StateFlow<SecondaryOutputFailure?> = mutableFailure

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { playback.transportState.collect(::applyTransportState) }
        scope.launch { playback.committedEvents.collect(::applyCommittedEvent) }
    }

    fun setVisible(isVisible: Boolean) {
        visible = isVisible
        if (!isVisible) stopEffects()
    }

    fun stopEffects() {
        pulseGeneration.incrementAndGet()
        runOutput(SecondaryOutput.HAPTIC, "cancel") { haptics.cancel() }
        runOutput(SecondaryOutput.TORCH, "off") { flashlight.turnFlashlightOff() }
    }

    internal fun applyTransportState(state: PlaybackTransportState) {
        if (state !is PlaybackTransportState.Playing) stopEffects()
    }

    internal fun applyCommittedEvent(event: PlaybackCommittedEvent) {
        val rendered = event as? PlaybackCommittedEvent.Rendered ?: return
        val playing = playback.transportState.value as? PlaybackTransportState.Playing ?: return
        if (!visible || rendered.sessionId != playing.context.sessionId) return
        val delay = (rendered.presentation as? EventPresentation.Correlated)
            ?.presentationNanoTime
            ?.minus(nanoTime())
            ?.coerceAtLeast(0)
            ?: 0
        scheduler.schedule(delay) { dispatch(rendered) }
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
            runOutput(SecondaryOutput.TORCH, "failsafe off") { flashlight.turnFlashlightOff() }
            return
        }
        scheduleTorchOff(generation, TORCH_PULSE_NANOS, "pulse off")
        scheduleTorchOff(generation, TORCH_FAILSAFE_NANOS, "failsafe off")
    }

    private fun scheduleTorchOff(generation: Long, delayNanos: Long, operation: String) {
        scheduler.schedule(delayNanos) {
            if (pulseGeneration.get() == generation) {
                runOutput(SecondaryOutput.TORCH, operation) { flashlight.turnFlashlightOff() }
            }
        }
    }

    private fun runOutput(
        output: SecondaryOutput,
        operation: String,
        action: () -> Unit
    ): Boolean = try {
        action()
        true
    } catch (failure: Exception) {
        mutableFailure.value = SecondaryOutputFailure(
            output,
            operation,
            failure.message ?: failure::class.java.simpleName
        )
        false
    }

    private companion object {
        const val TORCH_PULSE_NANOS = 40_000_000L
        const val TORCH_FAILSAFE_NANOS = 250_000_000L
    }
}
