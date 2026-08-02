package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import com.bfunkstudios.beatclikr.data.PracticeItemSnapshot
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface PlaybackObservation {
    val transportState: StateFlow<PlaybackTransportState>
    val committedEvents: SharedFlow<PlaybackCommittedEvent>
}

interface IAudioPlayerService {
    var isMuted: Boolean
    var soundBank: SoundBank
    fun setupAudioPlayer(beatResourceId: Int, rhythmResourceId: Int)
    fun startMetronome(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>? = null,
        alternateSixteenth: Boolean = false,
        practiceItem: PracticeItemSnapshot = PracticeItemSnapshot.metronome()
    )
    fun replaceMetronome(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>? = null,
        alternateSixteenth: Boolean = false,
        practiceItem: PracticeItemSnapshot = PracticeItemSnapshot.metronome()
    )
    fun stopIfCurrent(expectedSessionId: PlaybackSessionId)
    fun stopPlayback()
    fun updateTempo(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>? = null,
        alternateSixteenth: Boolean = false
    )
    fun startPolyrhythm(
        bpm: Float,
        beats: Int,
        against: Int,
        practiceItem: PracticeItemSnapshot = PracticeItemSnapshot.polyrhythm()
    )
    fun prewarmAudioTrack()
    fun prepareAudioTrackSounds(soundFiles: Collection<SoundFile>)
    fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot?
    fun release()
}
