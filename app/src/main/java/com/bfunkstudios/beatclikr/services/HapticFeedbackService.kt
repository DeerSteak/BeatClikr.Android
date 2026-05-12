package com.bfunkstudios.beatclikr.services

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticFeedbackService(private val context: Context) : IHapticFeedbackService {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun playBeatHaptic() {
        vibrate(durationMillis = 28L, amplitude = 255)
    }

    override fun playRhythmHaptic() {
        vibrate(durationMillis = 14L, amplitude = 96)
    }

    private fun vibrate(durationMillis: Long, amplitude: Int) {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMillis, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMillis)
        }
    }
}
