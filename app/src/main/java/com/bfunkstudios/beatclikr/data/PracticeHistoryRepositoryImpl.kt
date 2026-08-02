package com.bfunkstudios.beatclikr.data

import com.bfunkstudios.beatclikr.data.db.PracticeHistoryDao
import com.bfunkstudios.beatclikr.services.IPracticeReminderScheduler
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class PracticeHistoryRepositoryImpl @Inject constructor(
    private val dao: PracticeHistoryDao,
    private val reminderScheduler: IPracticeReminderScheduler
) : PracticeHistoryRepository {
    override fun getAllSessions() = dao.getAllSessions().map { sessions ->
        sessions.mapNotNull { session ->
            session.copy(
                songs = session.songs.filter { it.durationNanos >= QUALIFYING_DURATION_NANOS }
            ).takeIf { it.songs.isNotEmpty() }
        }
    }

    override suspend fun getAccountingCheckpoint(): PracticeAccountingCheckpoint? =
        dao.getAccountingCheckpoint()

    override suspend fun applyAccountingUpdate(
        day: PracticeDayIdentity?,
        item: PracticeItemSnapshot?,
        durationNanos: Long,
        periodIncrement: Int,
        checkpoint: PracticeAccountingCheckpoint
    ) {
        dao.applyAccountingUpdate(day, item, durationNanos, periodIncrement, checkpoint)
        runCatching { reminderScheduler.rescheduleIfEnabled() }
    }

    companion object {
        const val QUALIFYING_DURATION_NANOS = 30_000_000_000L
    }
}
