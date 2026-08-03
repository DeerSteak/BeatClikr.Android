package com.bfunkstudios.beatclikr.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class BpmFormatterTest {
    @Test
    fun `whole bpm has no decimal noise`() {
        assertEquals("120", formatBpm(120f, Locale.US))
    }

    @Test
    fun `imported decimal bpm keeps at most two digits`() {
        assertEquals("120.25", formatBpm(120.25f, Locale.US))
        assertEquals("120,5", formatBpm(120.5f, Locale.forLanguageTag("es")))
    }
}
