package com.nofar.core.location

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reference-counted lifecycle for GPS updates — starts on first foreground consumer,
 * stops when Explore/Home are inactive (AC-3.6, Requirements §8).
 */
@Singleton
class LocationController
@Inject
constructor(private val locationRepository: LocationRepository) {
    private val activeTokens = mutableSetOf<String>()

    val isActive: Boolean
        get() = activeTokens.isNotEmpty()

    fun acquire(token: String) {
        val wasEmpty = activeTokens.isEmpty()
        activeTokens.add(token)
        if (wasEmpty) {
            locationRepository.start()
        }
    }

    fun release(token: String) {
        activeTokens.remove(token)
        if (activeTokens.isEmpty()) {
            locationRepository.stop()
        }
    }
}
