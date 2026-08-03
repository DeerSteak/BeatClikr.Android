package com.bfunkstudios.beatclikr.ui

import android.view.Choreographer

internal fun pulseAlpha(frameTimeNanos: Long, startedAtNanos: Long, durationNanos: Long): Float {
    val progress = ((frameTimeNanos - startedAtNanos).toDouble() / durationNanos).coerceIn(0.0, 1.0)
    val remaining = 1.0 - progress
    return (remaining * remaining).toFloat()
}

internal class ChoreographerPulseLoop(
    private val shouldContinue: () -> Boolean,
    private val onFrame: (Long) -> Unit
) {
    private var choreographer: Choreographer? = null
    private var callback: Choreographer.FrameCallback? = null

    fun start() {
        if (callback != null) return
        val frames = choreographer ?: Choreographer.getInstance().also { choreographer = it }
        val next = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!shouldContinue()) {
                    callback = null
                    return
                }
                onFrame(frameTimeNanos)
                frames.postFrameCallback(this)
            }
        }
        callback = next
        frames.postFrameCallback(next)
    }

    fun stop() {
        callback?.let { choreographer?.removeFrameCallback(it) }
        callback = null
    }
}
