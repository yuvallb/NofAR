package com.nofar.feature.explore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreLocationAccuracyTest {
    @Test
    fun isDegraded_falseWhenAtThreshold() {
        assertFalse(ExploreLocationAccuracy.isDegraded(accuracyMeters = 30f, thresholdMeters = 30f))
    }

    @Test
    fun isDegraded_falseWhenBelowThreshold() {
        assertFalse(ExploreLocationAccuracy.isDegraded(accuracyMeters = 15f, thresholdMeters = 30f))
    }

    @Test
    fun isDegraded_trueWhenAboveThreshold() {
        assertTrue(ExploreLocationAccuracy.isDegraded(accuracyMeters = 31f, thresholdMeters = 30f))
    }
}
