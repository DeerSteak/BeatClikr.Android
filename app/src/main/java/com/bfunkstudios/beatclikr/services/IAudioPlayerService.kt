package com.bfunkstudios.beatclikr.services

import com.bfunkstudios.beatclikr.data.SoundFile

interface IAudioPlayerService {
    var delegate: MetronomeAudioEngineDelegate?
    var polyrhythmDelegate: PolyrhythmAudioEngineDelegate?
    var isMuted: Boolean
    var useAudioTrack: Boolean
    var useSyntheticAudioTrackSounds: Boolean
    fun setupAudioPlayer(beatResourceId: Int, rhythmResourceId: Int)
    fun startMetronome(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>? = null,
        alternateSixteenth: Boolean = false
    )
    fun stopMetronome()
    fun updateTempo(
        bpm: Float,
        subdivisions: Int,
        accentPattern: List<Boolean>? = null,
        alternateSixteenth: Boolean = false
    )
    fun startPolyrhythm(bpm: Float, beats: Int, against: Int)
    fun stopPolyrhythm()
    fun prewarmAudioTrack()
    fun prepareAudioTrackSounds(soundFiles: Collection<SoundFile>)
    fun getAudioTrackMetricsSnapshot(): AudioTrackMetricsSnapshot?
    fun release()
}
