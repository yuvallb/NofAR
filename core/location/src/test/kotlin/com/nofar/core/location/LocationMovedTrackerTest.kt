package com.nofar.core.location

import com.nofar.core.model.UserLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationMovedTrackerTest {
    @Test
    fun emitsWhenMovedBeyondVisibilityThreshold() {
        val tracker = LocationMovedTracker()
        val first = userAt(32.0, 35.0)
        val nearby = userAt(32.0001, 35.0001)
        val far = userAt(32.001, 35.001)

        assertNull(tracker.onLocationUpdate(first))
        assertNull(tracker.onLocationUpdate(nearby))
        assertEquals(far, tracker.onLocationUpdate(far))
    }

    @Test
    fun resetClearsAnchor() {
        val tracker = LocationMovedTracker()
        val first = userAt(32.0, 35.0)
        val second = userAt(32.001, 35.001)

        tracker.onLocationUpdate(first)
        tracker.reset()
        assertNull(tracker.onLocationUpdate(second))
        assertEquals(
            userAt(32.002, 35.002),
            tracker.onLocationUpdate(userAt(32.002, 35.002))
        )
    }

    private fun userAt(lat: Double, lon: Double): UserLocation = UserLocation(
        latitude = lat,
        longitude = lon,
        altitudeMeters = 100.0,
        accuracyMeters = 5f,
        timestampMillis = 1L
    )
}
