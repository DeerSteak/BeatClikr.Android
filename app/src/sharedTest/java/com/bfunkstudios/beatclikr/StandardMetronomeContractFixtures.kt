package com.bfunkstudios.beatclikr

internal enum class ContractSoundRole { BEAT, RHYTHM }

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
            tick,
            isBeat,
            if (isBeat) ContractSoundRole.BEAT else ContractSoundRole.RHYTHM
        )
    }
}

internal object StandardMetronomeContractFixtures {
    val tempoCases = listOf(
        StandardMetronomeFixture(30f, 4),
        StandardMetronomeFixture(137.5f, 4),
        StandardMetronomeFixture(240f, 4)
    )
    val grooveCases = (1..4).map { StandardMetronomeFixture(240f, it) }
}
