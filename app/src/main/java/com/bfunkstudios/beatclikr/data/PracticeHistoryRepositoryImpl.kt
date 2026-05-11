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

    override suspend fun recordSongPlayed(song: Song) {
        val session = getOrCreateTodaysSession()
        record(PracticedSong.fromSong(song, session.id), incrementsExisting = true)
    }

    override suspend fun recordMetronomePractice() {
        val session = getOrCreateTodaysSession()
        record(PracticedSong.metronome(session.id), incrementsExisting = false)
    }

    override suspend fun recordPolyrhythmPractice() {
        val session = getOrCreateTodaysSession()
        record(PracticedSong.polyrhythm(session.id), incrementsExisting = false)
    }

    private suspend fun record(song: PracticedSong, incrementsExisting: Boolean) {
        val existing = dao.getSessionWithSongsForDay(todayStart(), todayEnd())
            ?.songs?.firstOrNull { it.songId == song.songId }
        when {
            existing != null && incrementsExisting ->
                dao.updatePracticedSong(existing.copy(timesPracticed = existing.timesPracticed + 1))
            existing == null ->
                dao.insertPracticedSong(song)
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

    // Uses device local time deliberately — sessions are grouped by the day the user practiced,
    // which should match their local clock, not UTC.
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
}
