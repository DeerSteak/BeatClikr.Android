package com.bfunkstudios.beatclikr.services

interface IFlashlightService {
    val hasFlashlight: Boolean
    fun turnFlashlightOn()
    fun turnFlashlightOff()
}
