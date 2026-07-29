package com.bfunkstudios.beatclikr.music

sealed interface PlaybackInputResult<out T> {
    data class Accepted<T>(val value: T) : PlaybackInputResult<T>

    data class Rejected(val failure: PlaybackInputFailure) : PlaybackInputResult<Nothing>
}

sealed interface PlaybackInputFailure {
    data class InvalidDomainInput(val diagnostic: String) : PlaybackInputFailure
}

object PlaybackInputBoundary {
    fun <T> translate(operation: () -> T): PlaybackInputResult<T> =
        try {
            PlaybackInputResult.Accepted(operation())
        } catch (failure: IllegalArgumentException) {
            PlaybackInputResult.Rejected(
                PlaybackInputFailure.InvalidDomainInput(
                    failure.message ?: "Domain input violated an invariant"
                )
            )
        }
}
