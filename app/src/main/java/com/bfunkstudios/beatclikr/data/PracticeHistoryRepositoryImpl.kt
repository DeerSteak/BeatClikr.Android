package com.bfunkstudios.beatclikr.data

import com.bfunkstudios.beatclikr.data.db.PracticeHistoryDao
import com.bfunkstudios.beatclikr.services.IPracticeReminderScheduler
import java.util.Calendar
import javax.inject.Inject

class PracticeHistoryRepositoryImpl @Inject constructor(
    private val dao: PracticeHistoryDao,
    private val reminderScheduler: IPracticeReminderScheduler
) : PracticeHistoryRepository {

    override fun getAllSessions() = dao.getAllSessions()

    override suspend fun recordSongPlayed(song: Song) =
        record(PracticedSong.fromSong(song, PLACEHOLDER_SESSION_ID), incrementsExisting = true)

    override suspend fun recordMetronomePractice() =
        record(PracticedSong.metronome(PLACEHOLDER_SESSION_ID), incrementsExisting = false)

    override suspend fun recordPolyrhythmPractice() =
        record(PracticedSong.polyrhythm(PLACEHOLDER_SESSION_ID), incrementsExisting = false)

    private suspend fun record(template: PracticedSong, incrementsExisting: Boolean) {
        val session = getOrCreateTodaysSession()
        val withSession = template.copy(sessionId = session.id)
        val existing = dao.getSessionWithSongsForDay(todayStart(), todayEnd())
            ?.songs?.firstOrNull { it.songId == template.songId }
        when {
            existing != null && incrementsExisting ->
                dao.updatePracticedSong(existing.copy(timesPracticed = existing.timesPracticed + 1))
            existing == null ->
                dao.insertPracticedSong(withSession)
        }
        reminderScheduler.rescheduleIfEnabled()
    }

    private suspend fun getOrCreateTodaysSession(): PracticeSession {
        val existing = dao.getSessionForDay(todayStart(), todayEnd())
        if (existing != null) return existing
        val session = PracticeSession(date = System.currentTimeMillis())
        dao.upsertSession(session)
        return session
    }

    private fun todayStart(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun todayEnd(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }

    companion object {
        // Placeholder replaced by getOrCreateTodaysSession before any DB write
        private val PLACEHOLDER_SESSION_ID = java.util.UUID.randomUUID()
    }
}
