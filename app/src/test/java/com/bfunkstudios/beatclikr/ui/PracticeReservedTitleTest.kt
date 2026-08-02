package com.bfunkstudios.beatclikr.ui

import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.data.PracticedSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticeReservedTitleTest {
    @Test
    fun reservedIdsResolveToLocalizedResources() {
        assertEquals(
            R.string.instant_metronome,
            reservedPracticeTitleResource(PracticedSong.METRONOME_SONG_ID)
        )
        assertEquals(
            R.string.polyrhythm,
            reservedPracticeTitleResource(PracticedSong.POLYRHYTHM_SONG_ID)
        )
        assertNull(reservedPracticeTitleResource("user-song"))
    }
}
