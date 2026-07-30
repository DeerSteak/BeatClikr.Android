package com.bfunkstudios.beatclikr.services

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.AudioDeviceInfo
import android.media.AudioRouting

class AudioTrackRenderBackend(
    private val audioManager: AudioManager? = null,
    private val routeChangeObserver: (
        previous: AudioOutputRoute,
        current: AudioOutputRoute
    ) -> Unit = { _, _ -> }
) : AudioRenderBackend {
    private var failureSink = AudioBackendFailureSink {}
    private var track: AudioTrack? = null
    private var channelAdapter: MonoPcmChannelAdapter? = null
    private var streamProperties: AudioBackendStreamProperties? = null
    @Volatile
    private var currentRoute = AudioOutputRoute.UNKNOWN
    private val routingListener = AudioRouting.OnRoutingChangedListener { routing ->
        val previous = currentRoute
        val current = (routing as? AudioTrack)?.routedDevice.toOutputRoute()
        currentRoute = current
        if (shouldReportRouteChange(previous, current)) {
            routeChangeObserver(previous, current)
        }
    }
    private val platformTimestamp = AudioTimestamp()

    override fun open(
        request: AudioBackendOpenRequest,
        failureSink: AudioBackendFailureSink
    ): AudioBackendStreamProperties? {
        this.failureSink = failureSink
        if (track != null) {
            report(AudioBackendOperation.OPEN, AudioBackendFailureCode.INVALID_CONFIGURATION)
            return null
        }
        return try {
            val opened = buildTrack(request)
            if (opened.state != AudioTrack.STATE_INITIALIZED) {
                opened.release()
                report(AudioBackendOperation.OPEN, AudioBackendFailureCode.STREAM_UNAVAILABLE)
                null
            } else {
                val channelCount = opened.format.channelCount
                val bufferFrames = opened.bufferSizeInFrames
                val burstFrames = resolveBurstFrames().coerceAtMost(bufferFrames)
                track = opened
                channelAdapter = MonoPcmChannelAdapter(bufferFrames, channelCount)
                currentRoute = opened.routedDevice.toOutputRoute()
                opened.addOnRoutingChangedListener(routingListener, null)
                AudioBackendStreamProperties(
                    sampleRate = opened.sampleRate,
                    channelCount = channelCount,
                    burstFrames = burstFrames,
                    bufferFrames = bufferFrames,
                    performanceMode = opened.performanceMode.toBackendPerformanceMode(),
                    backend = AudioBackendType.AUDIO_TRACK,
                    route = currentRoute
                ).also { streamProperties = it }
            }
        } catch (_: IllegalArgumentException) {
            report(AudioBackendOperation.OPEN, AudioBackendFailureCode.INVALID_CONFIGURATION)
            null
        } catch (_: UnsupportedOperationException) {
            report(AudioBackendOperation.OPEN, AudioBackendFailureCode.STREAM_UNAVAILABLE)
            null
        } catch (_: IllegalStateException) {
            report(AudioBackendOperation.OPEN, AudioBackendFailureCode.STREAM_UNAVAILABLE)
            null
        }
    }

    override fun start(): Boolean {
        val opened = track
        if (opened == null) {
            report(AudioBackendOperation.START, AudioBackendFailureCode.START_REJECTED)
            return false
        }
        return try {
            opened.play()
            currentRoute = opened.routedDevice.toOutputRoute()
            true
        } catch (_: IllegalStateException) {
            report(AudioBackendOperation.START, AudioBackendFailureCode.START_REJECTED)
            false
        }
    }

    override fun render(
        monoPcm: ShortArray,
        frameOffset: Int,
        frameCount: Int,
        startFrame: Long
    ): Int {
        val opened = track
        val adapter = channelAdapter
        if (opened == null || adapter == null || startFrame < 0) {
            report(AudioBackendOperation.RENDER, AudioBackendFailureCode.INVALID_CONFIGURATION)
            return 0
        }
        val output = adapter.adapt(monoPcm, frameOffset, frameCount)
        if (output == null) {
            report(AudioBackendOperation.RENDER, AudioBackendFailureCode.INVALID_CONFIGURATION)
            return 0
        }
        val sampleCount = frameCount * adapter.channelCount
        val writtenSamples = try {
            opened.write(output, 0, sampleCount, AudioTrack.WRITE_BLOCKING)
        } catch (_: IllegalStateException) {
            AudioTrack.ERROR_INVALID_OPERATION
        }
        if (writtenSamples < 0 || writtenSamples % adapter.channelCount != 0) {
            report(AudioBackendOperation.RENDER, audioTrackWriteFailureCode(writtenSamples))
            return 0
        }
        return writtenSamples / adapter.channelCount
    }

    override fun stop(): Boolean {
        val opened = track ?: return true
        track = null
        channelAdapter = null
        streamProperties = null
        currentRoute = AudioOutputRoute.UNKNOWN
        opened.removeOnRoutingChangedListener(routingListener)
        return try {
            if (opened.playState == AudioTrack.PLAYSTATE_PLAYING) opened.pause()
            opened.flush()
            opened.release()
            true
        } catch (_: IllegalStateException) {
            opened.release()
            report(AudioBackendOperation.STOP, AudioBackendFailureCode.INTERNAL_ERROR)
            false
        }
    }

    override fun timestamp(destination: AudioFrameTimestamp): Boolean {
        val opened = track
        if (opened == null) {
            report(AudioBackendOperation.TIMESTAMP, AudioBackendFailureCode.TIMESTAMP_UNAVAILABLE)
            return false
        }
        return if (opened.getTimestamp(platformTimestamp)) {
            destination.framePosition = platformTimestamp.framePosition
            destination.monotonicTimeNanos = platformTimestamp.nanoTime
            true
        } else {
            report(AudioBackendOperation.TIMESTAMP, AudioBackendFailureCode.TIMESTAMP_UNAVAILABLE)
            false
        }
    }

    override fun underrunCount(): Int = track?.underrunCount ?: 0

    override fun streamProperties(): AudioBackendStreamProperties? = streamProperties

    override fun currentRoute(): AudioOutputRoute = currentRoute

    @Suppress("DEPRECATION")
    private fun buildTrack(request: AudioBackendOpenRequest): AudioTrack {
        val channelMask = when (request.preferredChannelCount) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            else -> throw IllegalArgumentException("Unsupported channel count")
        }
        val minimumBytes = AudioTrack.getMinBufferSize(
            request.preferredSampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimumBytes <= 0) throw UnsupportedOperationException("No supported stream")
        val requestedBytes = Math.multiplyExact(
            Math.multiplyExact(request.preferredBufferFrames, request.preferredChannelCount),
            Short.SIZE_BYTES
        )
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(request.preferredSampleRate)
            .setChannelMask(channelMask)
            .build()
        return AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minimumBytes, requestedBytes))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    private fun resolveBurstFrames(): Int =
        audioManager
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: DEFAULT_BURST_FRAMES

    private fun report(operation: AudioBackendOperation, code: AudioBackendFailureCode) {
        failureSink.report(AudioBackendFailure(operation, code))
    }

    private fun Int.toBackendPerformanceMode(): AudioBackendPerformanceMode = when (this) {
        AudioTrack.PERFORMANCE_MODE_NONE -> AudioBackendPerformanceMode.NONE
        AudioTrack.PERFORMANCE_MODE_LOW_LATENCY -> AudioBackendPerformanceMode.LOW_LATENCY
        AudioTrack.PERFORMANCE_MODE_POWER_SAVING -> AudioBackendPerformanceMode.POWER_SAVING
        else -> AudioBackendPerformanceMode.UNKNOWN
    }

    private fun AudioDeviceInfo?.toOutputRoute(): AudioOutputRoute = when (this?.type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutputRoute.BUILT_IN
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG -> AudioOutputRoute.WIRED
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_USB_HEADSET -> AudioOutputRoute.USB
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> AudioOutputRoute.BLUETOOTH
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC -> AudioOutputRoute.HDMI
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> AudioOutputRoute.REMOTE
        else -> AudioOutputRoute.UNKNOWN
    }

    private companion object {
        const val DEFAULT_BURST_FRAMES = 192
    }
}

internal fun audioTrackWriteFailureCode(result: Int): AudioBackendFailureCode =
    if (result == AudioTrack.ERROR_DEAD_OBJECT) {
        AudioBackendFailureCode.DEVICE_DISCONNECTED
    } else {
        AudioBackendFailureCode.WRITE_FAILED
    }

internal fun shouldReportRouteChange(
    previous: AudioOutputRoute,
    current: AudioOutputRoute
): Boolean = previous != AudioOutputRoute.UNKNOWN && current != previous
