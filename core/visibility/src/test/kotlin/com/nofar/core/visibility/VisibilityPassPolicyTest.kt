package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.UserLocation
import org.junit.Test

class VisibilityPassPolicyTest {
    @Test
    fun stationaryLocation_blocksPassUntilTwoSecondsElapsed() {
        val origin = locationAt(0L, 32.0, 35.0)
        assertThat(
            policyPass(
                location = locationAt(500L, 32.0, 35.00001),
                lastPassLocation = origin,
                lastPassAtMillis = 0L
            )
        ).isFalse()

        assertThat(
            policyPass(
                location = locationAt(2_500L, 32.0, 35.00001),
                lastPassLocation = origin,
                lastPassAtMillis = 0L
            )
        ).isTrue()
    }

    @Test
    fun largeMove_triggersPassBeforeIntervalElapsed() {
        val origin = locationAt(1_000L, 32.0, 35.0)
        assertThat(
            policyPass(
                location = locationAt(1_500L, 32.0, 35.0005),
                lastPassLocation = origin,
                lastPassAtMillis = 1_000L
            )
        ).isTrue()
    }

    @Test
    fun smallMoveWithinInterval_doesNotTriggerPass() {
        val origin = locationAt(1_000L, 32.0, 35.0)
        assertThat(
            policyPass(
                location = locationAt(1_500L, 32.0, 35.00001),
                lastPassLocation = origin,
                lastPassAtMillis = 1_000L
            )
        ).isFalse()
    }

    private fun policyPass(location: UserLocation, lastPassLocation: UserLocation?, lastPassAtMillis: Long): Boolean =
        VisibilityPassPolicy.shouldSchedulePass(
            location = location,
            lastPassLocation = lastPassLocation,
            lastPassAtMillis = lastPassAtMillis,
            force = false
        )

    private fun locationAt(timestampMillis: Long, lat: Double, lon: Double): UserLocation = UserLocation(
        latitude = lat,
        longitude = lon,
        altitudeMeters = 100.0,
        accuracyMeters = 5f,
        timestampMillis = timestampMillis
    )
}
