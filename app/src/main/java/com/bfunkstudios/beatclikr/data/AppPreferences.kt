package com.bfunkstudios.beatclikr.data

import android.content.Context
import androidx.core.content.edit

class AppPreferences(context: Context) : IAppPreferences {

    private val prefs = context.getSharedPreferences("beatclikr_preferences", Context.MODE_PRIVATE)

    // --- Instant mode ---

    override var instantBpm: Float
        get() = prefs.getFloat(Keys.INSTANT_BPM, 120f)
        set(value) = prefs.edit { putFloat(Keys.INSTANT_BPM, value) }

    override var instantGroove: Groove
        get() = Groove.valueOf(
            prefs.getString(Keys.INSTANT_SUBDIVISIONS, Groove.Quarter.name)!!
        )
        set(value) = prefs.edit { putString(Keys.INSTANT_SUBDIVISIONS, value.name) }

    override var instantBeatPattern: BeatPattern?
        get() = prefs.getString(Keys.INSTANT_BEAT_PATTERN, null)?.let { BeatPattern.fromRawValue(it) }
        set(value) = prefs.edit { putString(Keys.INSTANT_BEAT_PATTERN, value?.rawValue) }

    override var rampEnabled: Boolean
        get() = prefs.getBoolean(Keys.RAMP_ENABLED, false)
        set(value) = prefs.edit { putBoolean(Keys.RAMP_ENABLED, value) }

    override var rampIncrement: Int
        get() = prefs.getInt(Keys.RAMP_INCREMENT, 2).takeIf { it > 0 } ?: 2
        set(value) = prefs.edit { putInt(Keys.RAMP_INCREMENT, value) }

    override var rampInterval: Int
        get() = prefs.getInt(Keys.RAMP_INTERVAL, 8).takeIf { it > 0 } ?: 8
        set(value) = prefs.edit { putInt(Keys.RAMP_INTERVAL, value) }

    override var instantBeatSound: SoundFile
        get() = SoundFile.valueOf(
            prefs.getString(Keys.INSTANT_BEAT_SOUND, SoundFile.CLICK_HI.name)!!
        )
        set(value) = prefs.edit { putString(Keys.INSTANT_BEAT_SOUND, value.name) }

    override var instantRhythmSound: SoundFile
        get() = SoundFile.valueOf(
            prefs.getString(Keys.INSTANT_RHYTHM_SOUND, SoundFile.CLICK_LO.name)!!
        )
        set(value) = prefs.edit { putString(Keys.INSTANT_RHYTHM_SOUND, value.name) }

    // --- Playlist mode ---

    override var playlistBeatSound: SoundFile
        get() = SoundFile.valueOf(
            prefs.getString(Keys.PLAYLIST_BEAT_SOUND, SoundFile.CLICK_HI.name)!!
        )
        set(value) = prefs.edit { putString(Keys.PLAYLIST_BEAT_SOUND, value.name) }

    override var playlistRhythmSound: SoundFile
        get() = SoundFile.valueOf(
            prefs.getString(Keys.PLAYLIST_RHYTHM_SOUND, SoundFile.CLICK_LO.name)!!
        )
        set(value) = prefs.edit { putString(Keys.PLAYLIST_RHYTHM_SOUND, value.name) }

    // --- Behavior ---

    override var useVibration: Boolean
        get() = prefs.getBoolean(Keys.USE_VIBRATION, false)
        set(value) = prefs.edit { putBoolean(Keys.USE_VIBRATION, value) }

    override var useFlashlight: Boolean
        get() = prefs.getBoolean(Keys.USE_FLASHLIGHT, false)
        set(value) = prefs.edit { putBoolean(Keys.USE_FLASHLIGHT, value) }

    override var muteMetronome: Boolean
        get() = prefs.getBoolean(Keys.MUTE_METRONOME, false)
        set(value) = prefs.edit { putBoolean(Keys.MUTE_METRONOME, value) }

    override var keepScreenAwake: Boolean
        get() = prefs.getBoolean(Keys.KEEP_SCREEN_AWAKE, false)
        set(value) = prefs.edit { putBoolean(Keys.KEEP_SCREEN_AWAKE, value) }

    // --- Practice reminders ---

    override var practiceReminderEnabled: Boolean
        get() = prefs.getBoolean(Keys.REMINDER_ENABLED, false)
        set(value) = prefs.edit { putBoolean(Keys.REMINDER_ENABLED, value) }

    override var practiceReminderHour: Int
        get() = prefs.getInt(Keys.REMINDER_HOUR, 9)
        set(value) = prefs.edit { putInt(Keys.REMINDER_HOUR, value) }

    override var practiceReminderMinute: Int
        get() = prefs.getInt(Keys.REMINDER_MINUTE, 0)
        set(value) = prefs.edit { putInt(Keys.REMINDER_MINUTE, value) }

    private object Keys {
        const val INSTANT_BPM = "instant_bpm"
        const val INSTANT_SUBDIVISIONS = "instant_subdivisions"
        const val INSTANT_BEAT_PATTERN = "instant_beat_pattern"
        const val RAMP_ENABLED = "ramp_enabled"
        const val RAMP_INCREMENT = "ramp_increment"
        const val RAMP_INTERVAL = "ramp_interval"
        const val INSTANT_BEAT_SOUND = "instant_beat_sound"
        const val INSTANT_RHYTHM_SOUND = "instant_rhythm_sound"
        const val PLAYLIST_BEAT_SOUND = "playlist_beat_sound"
        const val PLAYLIST_RHYTHM_SOUND = "playlist_rhythm_sound"
        const val USE_VIBRATION = "use_vibration"
        const val USE_FLASHLIGHT = "use_flashlight"
        const val MUTE_METRONOME = "mute_metronome"
        const val KEEP_SCREEN_AWAKE = "keep_screen_awake"
        const val REMINDER_ENABLED = "reminder_enabled"
        const val REMINDER_HOUR = "reminder_hour"
        const val REMINDER_MINUTE = "reminder_minute"
    }
}
