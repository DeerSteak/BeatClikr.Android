package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundBank
import com.bfunkstudios.beatclikr.data.SoundFile
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface PlaybackObservation {
    val transportState: StateFlow<PlaybackTransportState>
    val committedEvents: SharedFlow<PlaybackCommittedEvent>
}

interface IAudioPlayerService {
    var delegate: MetronomeAudioEngineDelegate?
    var polyrhythmDelegate: PolyrhythmAudioEngineDelegate?
    var isMuted: Boolean
    var soundBank: SoundBank
    fun setupAudioPlayer(beatResourceId: Int, rhythmResourceId: Int)
    fun startMetronome(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>? = null,
        alternateSixteenth: Boolean = false
    )
    fun stopIfCurrent(expectedSessionId: PlaybackSessionId)
    fun stopPlayback()
    fun updateTempo(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>? = null,
        alternateSixteenth: Boolean = false
    )
    fun startPolyrhythm(bpm: Float, beats: Int, against: Int)
    fun prewarmAudioTrack()
    fun prepareAudioTrackSounds(soundFiles: Collection<SoundFile>)
    fun getFrameAudioMetricsSnapshot(): FrameAudioMetricsSnapshot?
    fun release()
}
