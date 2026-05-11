package com.bfunkstudios.beatclikr.data

import androidx.annotation.RawRes
import com.bfunkstudios.beatclikr.R

enum class SoundFile(
    val displayName: String,
    val fileName: String,
    @param:RawRes val resourceId: Int?
) {
    CLICK_HI("Click Hi", "clickhi_E5", R.raw.clickhi_e5),
    CLICK_LO("Click Lo", "clicklo_F5", R.raw.clicklo_f5),
    COWBELL("Cowbell", "cowbell_G#3", R.raw.cowbell_gsharp3),
    CRASH_L("Crash (Left)", "crashl_C#3", R.raw.crashl_csharp3),
    CRASH_R("Crash (Right)", "crashr_A3", R.raw.crashr_a3),
    HAT_CLOSED("Hi-Hat (Closed)", "hatclosed_F#2", R.raw.hatclosed_fsharp2),
    HAT_OPEN("Hi-Hat (Open)", "hatopen_A#2", R.raw.hatopen_asharp2),
    KICK("Kick", "kick_C2", R.raw.kick_c2),
    RIDE_EDGE("Ride (Edge)", "rideedge_D#3", R.raw.rideedge_dsharp3),
    RIDE_BELL("Ride (Bell)", "ridebell_F3", R.raw.ridebell_f3),
    SNARE("Snare", "snare_D2", R.raw.snare_d2),
    TAMB("Tambourine", "tamb_F#3", R.raw.tamb_fsharp3),
    TOM_HI("Tom (High)", "tomhi_D3", R.raw.tomhi_d3),
    TOM_LO("Tom (Low)", "tomlow_A2", R.raw.tomlow_a2),
    TOM_MID("Tom (Mid)", "tommid_B2", R.raw.tommid_b2);

    companion object {
        val beatSounds = listOf(
            CLICK_HI,
            CLICK_LO,
            COWBELL,
            CRASH_L,
            CRASH_R,
            HAT_CLOSED,
            HAT_OPEN,
            KICK,
            SNARE,
            TAMB,
            TOM_HI,
            TOM_MID,
            TOM_LO
        )

        val rhythmSounds = listOf(
            CLICK_HI,
            CLICK_LO,
            COWBELL,
            HAT_CLOSED,
            HAT_OPEN,
            KICK,
            RIDE_BELL,
            RIDE_EDGE,
            SNARE,
            TAMB,
            TOM_HI,
            TOM_MID,
            TOM_LO
        )
    }
}
