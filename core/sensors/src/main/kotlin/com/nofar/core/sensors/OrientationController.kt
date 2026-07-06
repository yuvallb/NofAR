package com.nofar.core.sensors

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrientationController
@Inject
constructor(private val orientationProvider: OrientationProvider) {
    private val activeTokens = mutableSetOf<String>()

    fun acquire(token: String) {
        val wasEmpty = activeTokens.isEmpty()
        activeTokens.add(token)
        if (wasEmpty) {
            orientationProvider.start()
        }
    }

    fun release(token: String) {
        activeTokens.remove(token)
        if (activeTokens.isEmpty()) {
            orientationProvider.stop()
        }
    }
}
