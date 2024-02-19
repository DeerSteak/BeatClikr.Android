package com.bfunkstudios.beatclikr.models

import java.util.UUID

data class Song(var title: String,
                var artist: String,
                var beatsPerMinute: Int,
                var beatsPerMeasure: Int,
                var subdivisions: Subdivisions,
                var liveSequence: Int?,
                var rehearsalSequence: Int?,
                val id: UUID = UUID.randomUUID()) {
}