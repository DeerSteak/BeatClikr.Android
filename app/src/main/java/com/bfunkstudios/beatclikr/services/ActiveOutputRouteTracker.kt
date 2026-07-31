package com.bfunkstudios.beatclikr.services

internal class ActiveOutputRouteTracker {
    private var route = AudioOutputRoute.UNKNOWN

    fun begin(initialRoute: AudioOutputRoute) {
        route = initialRoute
    }

    fun clear() {
        route = AudioOutputRoute.UNKNOWN
    }

    fun observe(current: AudioOutputRoute): PlaybackInterruptionReason? {
        val previous = route
        if (previous == AudioOutputRoute.UNKNOWN || current == previous) {
            route = current
            return null
        }
        if (current == AudioOutputRoute.UNKNOWN) {
            // Retain the last usable route so recovery reports the real route transition.
            return PlaybackInterruptionReason.RouteUnavailable(previous)
        }
        route = current
        return PlaybackInterruptionReason.RouteChanged(previous, current)
    }
}
