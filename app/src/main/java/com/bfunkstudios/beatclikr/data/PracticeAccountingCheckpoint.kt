package com.bfunkstudios.beatclikr.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "practice_accounting_checkpoint")
data class PracticeAccountingCheckpoint(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "acknowledged_lifecycle_sequence")
    val acknowledgedLifecycleSequence: Long = 0L,
    @ColumnInfo(name = "active_playback_session_id")
    val activePlaybackSessionId: Long? = null,
    @ColumnInfo(name = "active_item_id") val activeItemId: String? = null,
    @ColumnInfo(name = "last_checkpoint_elapsed_nanos")
    val lastCheckpointElapsedNanos: Long? = null,
    @ColumnInfo(name = "period_counted") val periodCounted: Boolean = false
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
