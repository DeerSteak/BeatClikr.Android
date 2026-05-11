package com.bfunkstudios.beatclikr.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat

class FlashlightService(private val context: Context) : IFlashlightService {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val torchCameraId: String? by lazy {
        runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val isBackCamera = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
                hasFlash && isBackCamera
            }
        }.getOrNull()
    }

    override val hasFlashlight: Boolean
        get() = torchCameraId != null

    override fun turnFlashlightOn() {
        setTorch(enabled = true)
    }

    override fun turnFlashlightOff() {
        setTorch(enabled = false)
    }

    private fun setTorch(enabled: Boolean) {
        val cameraId = torchCameraId ?: return
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        runCatching {
            cameraManager.setTorchMode(cameraId, enabled)
        }
    }
}
