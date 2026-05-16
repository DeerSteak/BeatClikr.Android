package com.bfunkstudios.beatclikr.data

import androidx.annotation.StringRes
import com.bfunkstudios.beatclikr.R

enum class SoundBank(@param:StringRes val labelRes: Int) {
    ACOUSTIC(R.string.settings_low_latency_sounds_acoustic),
    SYNTH(R.string.settings_low_latency_sounds_synth)
}
