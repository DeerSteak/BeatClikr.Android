package com.bfunkstudios.beatclikr.services

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import androidx.annotation.VisibleForTesting

internal class AudioDeviceTopologyMonitor(
    register: (AudioDeviceCallback) -> Unit,
    private val unregister: (AudioDeviceCallback) -> Unit,
    private val onTopologyChanged: () -> Unit
) {
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            onTopologyChanged()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            onTopologyChanged()
        }
    }

    init {
        register(callback)
    }

    fun release() {
        unregister(callback)
    }

    @VisibleForTesting
    internal fun callbackForTesting(): AudioDeviceCallback = callback
}
