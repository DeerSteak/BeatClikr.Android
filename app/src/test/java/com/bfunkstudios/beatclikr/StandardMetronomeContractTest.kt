package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.Groove
import org.junit.Assert.assertEquals
import org.junit.Test

class StandardMetronomeContractTest {

    @Test
    fun mt001_mt004_androidConstantsMatchRepresentativeIosFixtures() {
        assertEquals(30f, MetronomeConstants.MIN_BPM)
        assertEquals(240f, MetronomeConstants.MAX_BPM)
        assertEquals(
            listOf(1, 2, 3, 4),
            Groove.standardEntries.map { it.subdivisions }
        )
    }
}
