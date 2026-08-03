package com.bfunkstudios.beatclikr.data

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.bfunkstudios.beatclikr.R

enum class SoundFile(
    @param:StringRes val labelRes: Int,
    val fileName: String,
    @param:RawRes val resourceId: Int?,
    val synthFileName: String,
    @param:RawRes val synthResourceId: Int?
) {
    CLICK_HI(R.string.sound_click_hi, "clickhi_E5", R.raw.clickhi_e5, "synth_clickhi_E5", R.raw.synth_clickhi_e5),
    CLICK_LO(R.string.sound_click_lo, "clicklo_F5", R.raw.clicklo_f5, "synth_clicklo_F5", R.raw.synth_clicklo_f5),
    COWBELL(R.string.sound_cowbell, "cowbell_G#3", R.raw.cowbell_gsharp3, "synth_cowbell_G#3", R.raw.synth_cowbell_gsharp3),
    CRASH_L(R.string.sound_crash_left, "crashl_C#3", R.raw.crashl_csharp3, "synth_crashl_C#3", R.raw.synth_crashl_csharp3),
    CRASH_R(R.string.sound_crash_right, "crashr_A3", R.raw.crashr_a3, "synth_crashr_A3", R.raw.synth_crashr_a3),
    HAT_CLOSED(R.string.sound_hat_closed, "hatclosed_F#2", R.raw.hatclosed_fsharp2, "synth_hatclosed_F#2", R.raw.synth_hatclosed_fsharp2),
    HAT_OPEN(R.string.sound_hat_open, "hatopen_A#2", R.raw.hatopen_asharp2, "synth_hatopen_A#2", R.raw.synth_hatopen_asharp2),
    KICK(R.string.sound_kick, "kick_C2", R.raw.kick_c2, "synth_kick_C2", R.raw.synth_kick_c2),
    RIDE_EDGE(R.string.sound_ride_edge, "rideedge_D#3", R.raw.rideedge_dsharp3, "synth_rideedge_D#3", R.raw.synth_rideedge_dsharp3),
    RIDE_BELL(R.string.sound_ride_bell, "ridebell_F3", R.raw.ridebell_f3, "synth_ridebell_F3", R.raw.synth_ridebell_f3),
    SNARE(R.string.sound_snare, "snare_D2", R.raw.snare_d2, "synth_snare_D2", R.raw.synth_snare_d2),
    TAMB(R.string.sound_tambourine, "tamb_F#3", R.raw.tamb_fsharp3, "synth_tamb_F#3", R.raw.synth_tamb_fsharp3),
    TOM_HI(R.string.sound_tom_high, "tomhi_D3", R.raw.tomhi_d3, "synth_tomhi_D3", R.raw.synth_tomhi_d3),
    TOM_LO(R.string.sound_tom_low, "tomlow_A2", R.raw.tomlow_a2, "synth_tomlow_A2", R.raw.synth_tomlow_a2),
    TOM_MID(R.string.sound_tom_mid, "tommid_B2", R.raw.tommid_b2, "synth_tommid_B2", R.raw.synth_tommid_b2);

    fun fileNameFor(bank: SoundBank) = if (bank == SoundBank.SYNTH) synthFileName else fileName
    fun resourceIdFor(bank: SoundBank) = if (bank == SoundBank.SYNTH) synthResourceId else resourceId

    companion object {
        fun fromResourceId(resourceId: Int): SoundFile? =
            entries.firstOrNull { it.resourceId == resourceId }

        val beatSounds = listOf(
            CLICK_HI, CLICK_LO, COWBELL, CRASH_L, CRASH_R,
            HAT_CLOSED, HAT_OPEN, KICK, SNARE, TAMB,
            TOM_HI, TOM_MID, TOM_LO
        )

        val rhythmSounds = listOf(
            CLICK_HI, CLICK_LO, COWBELL, HAT_CLOSED, HAT_OPEN,
            KICK, RIDE_BELL, RIDE_EDGE, SNARE, TAMB,
            TOM_HI, TOM_MID, TOM_LO
        )
    }
}
