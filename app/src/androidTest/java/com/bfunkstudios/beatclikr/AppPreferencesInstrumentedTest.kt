package com.bfunkstudios.beatclikr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bfunkstudios.beatclikr.data.AppPreferences
import com.bfunkstudios.beatclikr.data.Groove
import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferencesInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val raw by lazy {
        context.getSharedPreferences("beatclikr_preferences", Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() = raw.edit().clear().commit().let { Unit }

    @After
    fun tearDown() = raw.edit().clear().commit().let { Unit }

    @Test
    fun corruptAndUnknownValuesFallBackWithoutCrashing() {
        raw.edit()
            .putString("instant_bpm", "not-a-number")
            .putString("instant_subdivisions", "unknown")
            .putString("instant_beat_sound", "missing")
            .putInt("reminder_enabled", 1)
            .putInt("reminder_hour", 99)
            .apply()

        val preferences = AppPreferences(context)
        assertEquals(60f, preferences.instantBpm)
        assertEquals(Groove.Quarter, preferences.instantGroove)
        assertEquals(SoundFile.CLICK_HI, preferences.instantBeatSound)
        assertEquals(false, preferences.practiceReminderEnabled)
        assertEquals(23, preferences.practiceReminderHour)
    }

    @Test
    fun legacyAndVersionedEnumsDecodeAndWritesAreBounded() {
        raw.edit()
            .putString("instant_subdivisions", "quarter_note")
            .putString("instant_beat_sound", "click_lo")
            .putBoolean("use_synthetic_audio_track_sounds", true)
            .apply()
        val preferences = AppPreferences(context)

        assertEquals(Groove.Quarter, preferences.instantGroove)
        assertEquals(SoundFile.CLICK_LO, preferences.instantBeatSound)
        assertEquals(SoundBank.SYNTH, preferences.soundBank)

        preferences.instantGroove = Groove.Triplet
        preferences.instantBpm = Float.POSITIVE_INFINITY
        preferences.polyrhythmBeats = 100
        preferences.rampIncrement = 9

        assertEquals("v1:Triplet", raw.getString("instant_subdivisions", null))
        assertEquals(60f, preferences.instantBpm)
        assertEquals(15, preferences.polyrhythmBeats)
        assertEquals(10, preferences.rampIncrement)
    }
}
