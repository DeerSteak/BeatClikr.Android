package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.services.IFlashlightService

class FakeFlashlightService : IFlashlightService {
    override val hasFlashlight: Boolean = true

    var onCount = 0
    var offCount = 0

    override fun turnFlashlightOn() {
        onCount++
    }

    override fun turnFlashlightOff() {
        offCount++
    }
}
