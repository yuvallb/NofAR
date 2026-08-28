package com.nofar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AltitudeReadingTest {
    @Test
    fun demDisagreementText_positiveDelta() {
        val reading = gpsReading(demMeters = 140, demDeltaMeters = 19)

        assertEquals("(140+19m)", reading.demDisagreementText)
    }

    @Test
    fun demDisagreementText_negativeDelta() {
        val reading = gpsReading(demMeters = 159, demDeltaMeters = -19)

        assertEquals("(159-19m)", reading.demDisagreementText)
    }

    @Test
    fun demDisagreementText_absentWhenDemMissing() {
        assertNull(gpsReading(demMeters = null, demDeltaMeters = null).demDisagreementText)
    }

    private fun gpsReading(demMeters: Int?, demDeltaMeters: Int?) = AltitudeReading(
        meters = 159,
        source = AltitudeSource.GPS,
        isEstimate = false,
        accuracyMeters = 3,
        accuracyIsVertical = true,
        demMeters = demMeters,
        demDeltaMeters = demDeltaMeters
    )
}
