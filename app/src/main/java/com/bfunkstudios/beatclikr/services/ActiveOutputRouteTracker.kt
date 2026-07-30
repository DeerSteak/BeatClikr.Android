package com.bfunkstudios.beatclikr.services

internal class ActiveOutputRouteTracker {
    private var route = AudioOutputRoute.UNKNOWN

    fun begin(initialRoute: AudioOutputRoute) {
        route = initialRoute
    }

    fun clear() {
        route = AudioOutputRoute.UNKNOWN
    }

    fun observe(current: AudioOutputRoute): PlaybackInterruptionReason.RouteChanged? {
        val previous = route
        route = current
        if (previous == AudioOutputRoute.UNKNOWN || current == previous) return null
        return PlaybackInterruptionReason.RouteChanged(previous, current)
    }
}
