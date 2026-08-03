package com.bfunkstudios.beatclikr.data

import android.content.Context
import androidx.core.content.edit
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.music.TempoRampConfiguration
import com.bfunkstudios.beatclikr.music.PolyrhythmConfiguration
import kotlin.math.abs

class AppPreferences(context: Context) : IAppPreferences {

    private val prefs = context.getSharedPreferences("beatclikr_preferences", Context.MODE_PRIVATE)

    // --- Instant mode ---

    override var instantBpm: Float
        get() = boundedBpm(Keys.INSTANT_BPM)
        set(value) = putBpm(Keys.INSTANT_BPM, value)

    override var instantGroove: Groove
        get() = enumValue(Keys.INSTANT_SUBDIVISIONS, Groove.Quarter, GROOVE_ALIASES)
        set(value) = putEnum(Keys.INSTANT_SUBDIVISIONS, value)

    override var instantBeatPattern: BeatPattern?
        get() = stringValue(Keys.INSTANT_BEAT_PATTERN)?.let { BeatPattern.fromRawValue(it) }
        set(value) = prefs.edit { putString(Keys.INSTANT_BEAT_PATTERN, value?.rawValue) }

    override var rampEnabled: Boolean
        get() = booleanValue(Keys.RAMP_ENABLED, false)
        set(value) = prefs.edit { putBoolean(Keys.RAMP_ENABLED, value) }

    override var rampIncrement: Int
        get() = supportedInt(Keys.RAMP_INCREMENT, 2, TempoRampConfiguration.supportedIncrements)
        set(value) = putSupportedInt(Keys.RAMP_INCREMENT, value, TempoRampConfiguration.supportedIncrements)

    override var rampInterval: Int
        get() = supportedInt(Keys.RAMP_INTERVAL, 8, TempoRampConfiguration.supportedIntervals)
        set(value) = putSupportedInt(Keys.RAMP_INTERVAL, value, TempoRampConfiguration.supportedIntervals)

    override var instantBeatSound: SoundFile
        get() = enumValue(Keys.INSTANT_BEAT_SOUND, SoundFile.CLICK_HI)
        set(value) = putEnum(Keys.INSTANT_BEAT_SOUND, value)

    override var instantRhythmSound: SoundFile
        get() = enumValue(Keys.INSTANT_RHYTHM_SOUND, SoundFile.CLICK_LO)
        set(value) = putEnum(Keys.INSTANT_RHYTHM_SOUND, value)

    // --- Playlist mode ---

    override var playlistBeatSound: SoundFile
        get() = enumValue(Keys.PLAYLIST_BEAT_SOUND, SoundFile.CLICK_HI)
        set(value) = putEnum(Keys.PLAYLIST_BEAT_SOUND, value)

    override var playlistRhythmSound: SoundFile
        get() = enumValue(Keys.PLAYLIST_RHYTHM_SOUND, SoundFile.CLICK_LO)
        set(value) = putEnum(Keys.PLAYLIST_RHYTHM_SOUND, value)

    // --- Polyrhythm mode ---

    override var polyrhythmBpm: Float
        get() = boundedBpm(Keys.POLYRHYTHM_BPM)
        set(value) = putBpm(Keys.POLYRHYTHM_BPM, value)

    override var polyrhythmBeats: Int
        get() = intValue(Keys.POLYRHYTHM_BEATS, 3).coerceIn(PolyrhythmConfiguration.SUPPORTED_COUNT)
        set(value) = prefs.edit {
            putInt(Keys.POLYRHYTHM_BEATS, value.coerceIn(PolyrhythmConfiguration.SUPPORTED_COUNT))
        }

    override var polyrhythmAgainst: Int
        get() = intValue(Keys.POLYRHYTHM_AGAINST, 2)
            .coerceIn(PolyrhythmConfiguration.SUPPORTED_COUNT)
        set(value) = prefs.edit {
            putInt(Keys.POLYRHYTHM_AGAINST, value.coerceIn(PolyrhythmConfiguration.SUPPORTED_COUNT))
        }

    override var polyrhythmBeatSound: SoundFile
        get() = enumValue(Keys.POLYRHYTHM_BEAT_SOUND, SoundFile.CLICK_HI)
        set(value) = putEnum(Keys.POLYRHYTHM_BEAT_SOUND, value)

    override var polyrhythmRhythmSound: SoundFile
        get() = enumValue(Keys.POLYRHYTHM_RHYTHM_SOUND, SoundFile.CLICK_LO)
        set(value) = putEnum(Keys.POLYRHYTHM_RHYTHM_SOUND, value)

    // --- Behavior ---

    override var useVibration: Boolean
        get() = booleanValue(Keys.USE_VIBRATION, false)
        set(value) = prefs.edit { putBoolean(Keys.USE_VIBRATION, value) }

    override var useFlashlight: Boolean
        get() = booleanValue(Keys.USE_FLASHLIGHT, false)
        set(value) = prefs.edit { putBoolean(Keys.USE_FLASHLIGHT, value) }

    override var alwaysUseDarkTheme: Boolean
        get() = booleanValue(Keys.ALWAYS_USE_DARK_THEME, true)
        set(value) = prefs.edit { putBoolean(Keys.ALWAYS_USE_DARK_THEME, value) }

    override var muteMetronome: Boolean
        get() = booleanValue(Keys.MUTE_METRONOME, false)
        set(value) = prefs.edit { putBoolean(Keys.MUTE_METRONOME, value) }

    override var keepScreenAwake: Boolean
        get() = booleanValue(Keys.KEEP_SCREEN_AWAKE, false)
        set(value) = prefs.edit { putBoolean(Keys.KEEP_SCREEN_AWAKE, value) }

    override var sixteenthAlternate: Boolean
        get() = booleanValue(Keys.SIXTEENTH_ALTERNATE, false)
        set(value) = prefs.edit { putBoolean(Keys.SIXTEENTH_ALTERNATE, value) }

    override var soundBank: SoundBank
        get() {
            val stored = stringValue(Keys.SOUND_BANK)
            if (stored != null) return decodeEnum(stored, SoundBank.ACOUSTIC)
            val legacy = booleanValue(Keys.LEGACY_SYNTHETIC_SOUNDS, false)
            return if (legacy) SoundBank.SYNTH else SoundBank.ACOUSTIC
        }
        set(value) = putEnum(Keys.SOUND_BANK, value)

    // --- Practice reminders ---

    override var practiceReminderEnabled: Boolean
        get() = booleanValue(Keys.REMINDER_ENABLED, false)
        set(value) = prefs.edit { putBoolean(Keys.REMINDER_ENABLED, value) }

    override var practiceReminderHour: Int
        get() = intValue(Keys.REMINDER_HOUR, 9).coerceIn(0, 23)
        set(value) = prefs.edit { putInt(Keys.REMINDER_HOUR, value.coerceIn(0, 23)) }

    override var practiceReminderMinute: Int
        get() = intValue(Keys.REMINDER_MINUTE, 0).coerceIn(0, 59)
        set(value) = prefs.edit { putInt(Keys.REMINDER_MINUTE, value.coerceIn(0, 59)) }

    override var practiceReminderNotificationsDeferred: Boolean
        get() = booleanValue(Keys.REMINDER_NOTIFICATIONS_DEFERRED, false)
        set(value) = prefs.edit { putBoolean(Keys.REMINDER_NOTIFICATIONS_DEFERRED, value) }

    override var practiceReminderNotificationPermissionRequested: Boolean
        get() = booleanValue(Keys.REMINDER_NOTIFICATION_PERMISSION_REQUESTED, false)
        set(value) = prefs.edit { putBoolean(Keys.REMINDER_NOTIFICATION_PERMISSION_REQUESTED, value) }

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
        const val POLYRHYTHM_BPM = "polyrhythm_bpm"
        const val POLYRHYTHM_BEATS = "polyrhythm_beats"
        const val POLYRHYTHM_AGAINST = "polyrhythm_against"
        const val POLYRHYTHM_BEAT_SOUND = "polyrhythm_beat_sound"
        const val POLYRHYTHM_RHYTHM_SOUND = "polyrhythm_rhythm_sound"
        const val USE_VIBRATION = "use_vibration"
        const val USE_FLASHLIGHT = "use_flashlight"
        const val ALWAYS_USE_DARK_THEME = "always_use_dark_theme"
        const val MUTE_METRONOME = "mute_metronome"
        const val KEEP_SCREEN_AWAKE = "keep_screen_awake"
        const val SIXTEENTH_ALTERNATE = "sixteenth_alternate"
        const val SOUND_BANK = "sound_bank"
        const val LEGACY_SYNTHETIC_SOUNDS = "use_synthetic_audio_track_sounds"
        const val REMINDER_ENABLED = "reminder_enabled"
        const val REMINDER_HOUR = "reminder_hour"
        const val REMINDER_MINUTE = "reminder_minute"
        const val REMINDER_NOTIFICATIONS_DEFERRED = "reminder_notifications_deferred"
        const val REMINDER_NOTIFICATION_PERMISSION_REQUESTED = "reminder_notification_permission_requested"
    }

    private fun boundedBpm(key: String): Float =
        floatValue(key, DEFAULT_BPM).takeIf { it.isFinite() }
            ?.coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM)
            ?: DEFAULT_BPM

    private fun putBpm(key: String, value: Float) {
        val bounded = value.takeIf { it.isFinite() }
            ?.coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM)
            ?: DEFAULT_BPM
        prefs.edit { putFloat(key, bounded) }
    }

    private fun stringValue(key: String): String? = prefs.all[key] as? String
    private fun booleanValue(key: String, default: Boolean) = prefs.all[key] as? Boolean ?: default
    private fun intValue(key: String, default: Int) = (prefs.all[key] as? Number)?.toInt() ?: default
    private fun floatValue(key: String, default: Float) = (prefs.all[key] as? Number)?.toFloat() ?: default

    private inline fun <reified T : Enum<T>> enumValue(
        key: String,
        default: T,
        aliases: Map<String, T> = emptyMap()
    ): T = stringValue(key)?.let { decodeEnum(it, default, aliases) } ?: default

    private inline fun <reified T : Enum<T>> decodeEnum(
        stored: String,
        default: T,
        aliases: Map<String, T> = emptyMap()
    ): T {
        val token = stored.substringAfter(CODEC_PREFIX, stored)
        return aliases[token.lowercase()]
            ?: enumValues<T>().firstOrNull { it.name.equals(token, ignoreCase = true) }
            ?: default
    }

    private fun putEnum(key: String, value: Enum<*>) =
        prefs.edit { putString(key, "$CODEC_PREFIX${value.name}") }

    private fun supportedInt(key: String, default: Int, supported: List<Int>): Int =
        nearestSupported(intValue(key, default), supported)

    private fun putSupportedInt(key: String, value: Int, supported: List<Int>) {
        prefs.edit { putInt(key, nearestSupported(value, supported)) }
    }

    private fun nearestSupported(value: Int, supported: List<Int>): Int =
        supported.minBy { abs(it.toLong() - value.toLong()) }

    private companion object {
        const val DEFAULT_BPM = 60f
        const val CODEC_PREFIX = "v1:"
        val GROOVE_ALIASES = mapOf(
            "quarter_note" to Groove.Quarter,
            "eighth_note" to Groove.Eighth,
            "sixteenth_note" to Groove.Sixteenth
        )
    }
}
