package com.bfunkstudios.beatclikr.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfunkstudios.beatclikr.constants.MetronomeConstants
import com.bfunkstudios.beatclikr.data.BeatPattern
import com.bfunkstudios.beatclikr.data.Groove
import com.bfunkstudios.beatclikr.data.Song
import com.bfunkstudios.beatclikr.data.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SongLibraryViewModel @Inject constructor(
    private val repository: SongRepository
) : ViewModel() {

    private val _selectedSong = MutableStateFlow<Song?>(null)

    val uiState: StateFlow<SongLibraryUiState> = combine(
        repository.getAllSongs(),
        _selectedSong
    ) { songs, selected ->
        SongLibraryUiState(songList = songs, selectedSong = selected)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SongLibraryUiState()
    )

    var currentSongId by mutableStateOf<UUID?>(null)
        private set

    fun setSelectedSong(uuid: UUID?) {
        _selectedSong.value = if (uuid == null) null
        else uiState.value.songList.find { it.id == uuid }
    }

    fun markSongPlaying(song: Song) {
        currentSongId = song.id
    }

    fun currentIndex(songs: List<Song> = uiState.value.songList): Int? =
        currentSongId?.let { id -> songs.indexOfFirst { it.id == id }.takeIf { it >= 0 } }

    fun currentSongTitle(songs: List<Song> = uiState.value.songList): String? =
        currentIndex(songs)?.let { songs[it].title }

    fun canGoPrevious(songs: List<Song> = uiState.value.songList): Boolean =
        currentIndex(songs)?.let { it > 0 } ?: false

    fun canGoNext(songs: List<Song> = uiState.value.songList): Boolean =
        currentIndex(songs)?.let { it < songs.lastIndex } ?: false

    fun playOrResume(songs: List<Song> = uiState.value.songList, onPlaySong: (Song) -> Unit) {
        val song = currentIndex(songs)?.let { songs[it] } ?: songs.firstOrNull() ?: return
        markSongPlaying(song)
        onPlaySong(song)
    }

    fun playPrevious(songs: List<Song> = uiState.value.songList, onPlaySong: (Song) -> Unit) {
        val index = currentIndex(songs) ?: return
        val song = songs.getOrNull(index - 1) ?: return
        markSongPlaying(song)
        onPlaySong(song)
    }

    fun playNext(songs: List<Song> = uiState.value.songList, onPlaySong: (Song) -> Unit) {
        val index = currentIndex(songs) ?: return
        val song = songs.getOrNull(index + 1) ?: return
        markSongPlaying(song)
        onPlaySong(song)
    }

    // --- Draft state for song detail form ---

    private var draftId: UUID = UUID.randomUUID()
    private var draftLiveSequence: Int? = null
    private var draftRehearsalSequence: Int? = null

    var draftTitle by mutableStateOf("")
        private set
    var draftArtist by mutableStateOf("")
        private set
    var draftBpm by mutableFloatStateOf(120f)
        private set
    var draftBeatsPerMeasure by mutableIntStateOf(4)
        private set
    var draftGroove by mutableStateOf(Groove.Eighth)
        private set
    var draftBeatPattern by mutableStateOf(BeatPattern.default)
        private set

    val isDraftValid: Boolean get() = draftTitle.isNotBlank() && draftArtist.isNotBlank()

    fun initDraft(song: Song?) {
        draftId = song?.id ?: UUID.randomUUID()
        draftTitle = song?.title ?: ""
        draftArtist = song?.artist ?: ""
        draftBpm = song?.beatsPerMinute ?: 120f
        draftBeatsPerMeasure = song?.beatsPerMeasure ?: 4
        draftGroove = song?.groove ?: Groove.Eighth
        draftBeatPattern = song?.beatPattern ?: BeatPattern.default
        draftLiveSequence = song?.liveSequence
        draftRehearsalSequence = song?.rehearsalSequence
    }

    fun updateDraftTitle(value: String) { draftTitle = value }
    fun updateDraftArtist(value: String) { draftArtist = value }
    fun updateDraftBpm(value: Float) { draftBpm = value.coerceIn(MetronomeConstants.MIN_BPM, MetronomeConstants.MAX_BPM) }
    fun updateDraftBeatsPerMeasure(value: Int) { draftBeatsPerMeasure = value.coerceIn(1, 16) }
    fun updateDraftGroove(value: Groove) { draftGroove = value }
    fun updateDraftBeatPattern(value: BeatPattern) { draftBeatPattern = value }

    fun saveDraft() {
        saveSong(Song(
            id = draftId,
            title = draftTitle,
            artist = draftArtist,
            beatsPerMinute = draftBpm,
            beatsPerMeasure = draftBeatsPerMeasure,
            groove = draftGroove,
            liveSequence = draftLiveSequence,
            rehearsalSequence = draftRehearsalSequence,
            beatPattern = if (draftGroove.isOddMeter) draftBeatPattern else null
        ))
    }

    // --- Repository operations ---

    fun saveSong(song: Song) {
        viewModelScope.launch { repository.upsert(song) }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch { repository.delete(song) }
    }
}
