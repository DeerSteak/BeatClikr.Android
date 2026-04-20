package com.bfunkstudios.beatclikr.constants

object MetronomeConstants {
    // BPM (Beats Per Minute) constraints
    const val MIN_BPM: Float = 30f
    const val MAX_BPM: Float = 240f
    const val DEFAULT_MIN_SLIDER_BPM: Float = 60f
    const val DEFAULT_MAX_SLIDER_BPM: Float = 180f

    // Visual sizing
    const val PLAYER_VIEW_DEFAULT_SIZE: Float = 80f
    const val PLAYER_VIEW_TOOLBAR_SIZE: Float = 30f

    // Animation - much more dramatic! (0.3 to 1.0 = 3.3x size change: 24dp -> 80dp)
    const val ICON_SCALE_MIN: Float = 0.3f
    const val ICON_SCALE_MAX: Float = 1.0f

    // Timing (in milliseconds)
    const val TIMER_CHECK_INTERVAL_MS: Long = 1L // 1ms for high-precision checks
    const val FIRST_BEAT_DELAY_MS: Long = 67L // 67ms delay to ensure timer starts before first beat
    const val LOOKAHEAD_TOLERANCE_MS: Long = 2L // 2ms lookahead for beat firing
}
