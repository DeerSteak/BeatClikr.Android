package com.bfunkstudios.beatclikr.constants

object MetronomeConstants {
    // BPM (Beats Per Minute) constraints
    const val MIN_BPM: Float = 30f
    const val MAX_BPM: Float = 240f

    // Visual sizing
    const val PLAYER_VIEW_DEFAULT_SIZE: Float = 80f
    const val PLAYER_VIEW_TOOLBAR_SIZE: Float = 30f

    // Animation
    const val ICON_SCALE_MIN: Float = 0.5f
    const val ICON_SCALE_MAX: Float = 1.0f

    // Audio
    const val MAX_SOUND_STREAMS: Int = 8

    // Timing (in milliseconds)
    const val TIMER_CHECK_INTERVAL_MS: Long = 1L // 1ms for high-precision checks
    const val FIRST_BEAT_DELAY_MS: Long = 67L // 67ms delay to ensure timer starts before first beat
    const val LOOKAHEAD_TOLERANCE_MS: Long = 2L // 2ms lookahead for beat firing
}
