package com.nofar.core.sensors

import com.nofar.core.location.LocationRepository
import com.nofar.core.model.UserLocation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class DeclinationCorrectorTest {
    @Test
    fun appliesFixedDeclinationAtKnownLocation() {
        val location =
            UserLocation(
                latitude = 32.8,
                longitude = 35.5,
                altitudeMeters = 500.0,
                accuracyMeters = 5f,
                timestampMillis = 1_700_000_000_000L
            )
        val repository = FakeLocationRepository(location)
        val declinationProvider =
            DeclinationProvider { _, _, _, _ -> 4.5f }
        val corrector = DeclinationCorrector(declinationProvider, repository)

        val trueAzimuth = corrector.magneticToTrueAzimuth(90f)

        assertEquals(94.5f, trueAzimuth, 0.001f)
    }

    @Test
    fun normalizesAzimuthToZeroThroughThreeSixty() {
        val location =
            UserLocation(
                latitude = 0.0,
                longitude = 0.0,
                altitudeMeters = 0.0,
                accuracyMeters = 10f,
                timestampMillis = 0L
            )
        val repository = FakeLocationRepository(location)
        val declinationProvider =
            DeclinationProvider { _, _, _, _ -> 20f }
        val corrector = DeclinationCorrector(declinationProvider, repository)

        val trueAzimuth = corrector.magneticToTrueAzimuth(350f)

        assertEquals(10f, trueAzimuth, 0.001f)
    }

    private class FakeLocationRepository(private val location: UserLocation) : LocationRepository {
        override val locationFlow = emptyFlow<UserLocation>()
        override val significantMoveFlow: SharedFlow<UserLocation> =
            MutableSharedFlow<UserLocation>().asSharedFlow()
        override val lastLocation: UserLocation? = location
        override val isActive: Boolean = true

        override fun start() = Unit

        override fun stop() = Unit

        override fun clearCachedLocation() = Unit

        override fun onPermissionRevoked() = Unit
    }
}
