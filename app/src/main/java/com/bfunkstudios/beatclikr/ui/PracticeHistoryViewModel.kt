package com.bfunkstudios.beatclikr.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bfunkstudios.beatclikr.R
import com.bfunkstudios.beatclikr.data.PracticedSong
import com.bfunkstudios.beatclikr.data.PracticeHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class StreakStatsUiState(
    val currentValue: Int,
    val currentValueLabel: String,
    val currentSubtitle: String,
    val longestValue: Int,
    val longestValueLabel: String,
    val longestSubtitle: String,
    val reminderNeeded: Boolean,
    val shareCardStreakDays: String
)

@HiltViewModel
class PracticeHistoryViewModel @Inject constructor(
    application: Application,
    private val repository: PracticeHistoryRepository
) : AndroidViewModel(application) {

    private val sessions = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedDate = MutableStateFlow(startOfDay(System.currentTimeMillis()))

    val selectedDate: StateFlow<Long> = _selectedDate

    val practiceDates: StateFlow<Set<Long>> = sessions
        .map { list -> list.map { startOfDay(it.session.date) }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val selectedDateSongs: StateFlow<List<PracticedSong>> = combine(sessions, _selectedDate) { list, date ->
        val songs = list.firstOrNull { startOfDay(it.session.date) == date }?.songs ?: emptyList()
        songs.sortedWith(
            compareBy<PracticedSong> { song ->
                when (song.songId) {
                    PracticedSong.METRONOME_SONG_ID -> 0
                    PracticedSong.POLYRHYTHM_SONG_ID -> 1
                    else -> 2
                }
            }
            .thenByDescending { it.timesPracticed }
            .thenBy { it.title }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Date selection ---

    fun selectDate(dateMs: Long) {
        _selectedDate.value = startOfDay(dateMs)
    }

    // --- Streak computations ---

    fun currentStreak(dates: Set<Long>): Int = currentStreakInfo(dates).first

    fun currentStreakSubtitle(dates: Set<Long>): String {
        val start = currentStreakInfo(dates).second ?: return getString(R.string.lets_go)
        return getString(R.string.streak_since, formatDate(start))
    }

    fun longestStreak(dates: Set<Long>): Int = longestStreakInfo(dates)?.first ?: 0

    fun longestStreakSubtitle(dates: Set<Long>): String {
        val info = longestStreakInfo(dates) ?: return getString(R.string.lets_go)
        val (_, start, end) = info
        val fmt = SimpleDateFormat("M/d/yy", Locale.getDefault())
        return if (start == end) fmt.format(start) else "${fmt.format(start)} – ${fmt.format(end)}"
    }

    fun reminderNeeded(dates: Set<Long>): Boolean {
        val today = startOfDay(System.currentTimeMillis())
        return currentStreak(dates) > 0 && !dates.contains(today)
    }

    fun streakStats(dates: Set<Long>): StreakStatsUiState {
        val current = currentStreak(dates)
        val longest = longestStreak(dates)
        return StreakStatsUiState(
            currentValue = current,
            currentValueLabel = dayCountLabel(current),
            currentSubtitle = currentStreakSubtitle(dates),
            longestValue = longest,
            longestValueLabel = dayCountLabel(longest),
            longestSubtitle = longestStreakSubtitle(dates),
            reminderNeeded = reminderNeeded(dates),
            shareCardStreakDays = current.toString()
        )
    }

    fun selectedDateTitle(dateMs: Long): String = formatDate(dateMs)

    fun shareText(dates: Set<Long>): String {
        val current = currentStreak(dates)
        val longest = longestStreak(dates)
        return when {
            current > 0 -> getString(R.string.share_streak_current, current)
            longest > 0 -> getString(R.string.share_streak_longest, longest)
            else -> getString(R.string.share_streak_none)
        }
    }

    // --- Private helpers ---

    private fun getString(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    private fun currentStreakInfo(dates: Set<Long>): Pair<Int, Long?> {
        val today = startOfDay(System.currentTimeMillis())
        val yesterday = previousDay(today)
        var check = if (dates.contains(today)) today else yesterday
        if (!dates.contains(check)) return Pair(0, null)
        var streak = 0
        while (dates.contains(check)) {
            streak++
            check = previousDay(check)
        }
        val start = nextDay(check)
        return Pair(streak, start)
    }

    private fun longestStreakInfo(dates: Set<Long>): Triple<Int, Long, Long>? {
        if (dates.isEmpty()) return null
        val sorted = dates.sorted()
        var bestStart = sorted[0]; var bestEnd = sorted[0]; var bestLen = 1
        var curStart = sorted[0]; var curLen = 1
        for (i in 1 until sorted.size) {
            if (nextDay(sorted[i - 1]) == sorted[i]) {
                curLen++
                if (curLen > bestLen) {
                    bestLen = curLen
                    bestStart = curStart
                    bestEnd = sorted[i]
                }
            } else {
                curStart = sorted[i]
                curLen = 1
            }
        }
        return Triple(bestLen, bestStart, bestEnd)
    }

    private fun previousDay(epochMs: Long): Long = Calendar.getInstance().run {
        timeInMillis = epochMs
        add(Calendar.DAY_OF_YEAR, -1)
        startOfDay(timeInMillis)
    }

    private fun nextDay(epochMs: Long): Long = Calendar.getInstance().run {
        timeInMillis = epochMs
        add(Calendar.DAY_OF_YEAR, 1)
        startOfDay(timeInMillis)
    }

    private fun formatDate(epochMs: Long): String =
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(epochMs)

    private fun dayCountLabel(value: Int): String =
        getApplication<Application>().resources.getQuantityString(R.plurals.day_count, value, value)

    companion object {
        fun startOfDay(epochMs: Long): Long = Calendar.getInstance().run {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }
    }
}
