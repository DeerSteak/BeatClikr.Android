package com.bfunkstudios.beatclikr

internal enum class ContractSoundRole {
    BEAT,
    RHYTHM
}

internal data class StandardMetronomeEventFixture(
    val tick: Int,
    val isBeat: Boolean,
    val soundRole: ContractSoundRole
)

internal data class StandardMetronomeFixture(
    val bpm: Float,
    val subdivisions: Int
) {
    val intervalNanos: Long
        get() = (60_000_000_000.0 / (bpm * subdivisions)).toLong()

    fun events(count: Int): List<StandardMetronomeEventFixture> = List(count) { index ->
        val tick = index % subdivisions
        val isBeat = tick == 0
        StandardMetronomeEventFixture(
            tick = tick,
            isBeat = isBeat,
            soundRole = if (isBeat) ContractSoundRole.BEAT else ContractSoundRole.RHYTHM
        )
    }
}

internal object StandardMetronomeContractFixtures {
    val tempoCases = listOf(
        StandardMetronomeFixture(bpm = 30f, subdivisions = 4),
        StandardMetronomeFixture(bpm = 137.5f, subdivisions = 4),
        StandardMetronomeFixture(bpm = 240f, subdivisions = 4)
    )

    val grooveCases = (1..4).map { subdivisions ->
        StandardMetronomeFixture(bpm = 240f, subdivisions = subdivisions)
    }
}
