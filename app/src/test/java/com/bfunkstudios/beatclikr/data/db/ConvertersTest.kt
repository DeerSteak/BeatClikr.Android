package com.bfunkstudios.beatclikr.data.db

import com.bfunkstudios.beatclikr.data.Groove
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun grooveCodecSupportsVersionedLegacyAndUnknownValues() {
        assertEquals("v1:Triplet", converters.grooveToString(Groove.Triplet))
        assertEquals(Groove.Eighth, converters.stringToGroove("Eighth"))
        assertEquals(Groove.Quarter, converters.stringToGroove("quarter_note"))
        assertEquals(Groove.Quarter, converters.stringToGroove("future-value"))
    }
}
