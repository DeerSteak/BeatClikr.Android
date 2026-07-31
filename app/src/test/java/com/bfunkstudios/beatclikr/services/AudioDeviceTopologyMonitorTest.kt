package com.bfunkstudios.beatclikr.services

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AudioDeviceTopologyMonitorTest {
    @Test
    fun registrationDeviceChangesAndReleaseUseOneCallback() {
        lateinit var registered: AudioDeviceCallback
        var unregistered: AudioDeviceCallback? = null
        var topologyChanges = 0
        val monitor = AudioDeviceTopologyMonitor(
            register = { registered = it },
            unregister = { unregistered = it },
            onTopologyChanged = { topologyChanges++ }
        )

        registered.onAudioDevicesAdded(emptyArray<AudioDeviceInfo>())
        registered.onAudioDevicesRemoved(emptyArray<AudioDeviceInfo>())
        monitor.release()

        assertEquals(2, topologyChanges)
        assertSame(registered, unregistered)
    }
}
