package com.nofar.core.model

/**
 * GPS fix from the location provider (Requirements §3.3).
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float,
    val timestampMillis: Long
)
