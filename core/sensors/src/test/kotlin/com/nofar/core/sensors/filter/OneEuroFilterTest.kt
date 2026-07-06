package com.nofar.core.sensors.filter

import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

class OneEuroFilterTest {
    @Test
    fun noisyAzimuthSignalHasLowerVarianceAfterFiltering() {
        val filter =
            AngularOneEuroFilter(
                minCutoff = 1.0,
                beta = 0.007,
                dCutoff = 1.0
            )
        val raw = noisyAzimuthSignal()
        val filtered = mutableListOf<Double>()
        raw.forEachIndexed { index, value ->
            filtered.add(filter.filter(value, index / 60.0))
        }

        assertTrue(variance(filtered) < variance(raw))
    }

    @Test
    fun azimuthWrapAroundDoesNotProduceSpike() {
        val filter =
            AngularOneEuroFilter(
                minCutoff = 1.0,
                beta = 0.007,
                dCutoff = 1.0
            )
        val t0 = filter.filter(359.0, 0.0)
        val t1 = filter.filter(1.0, 1 / 60.0)
        val t2 = filter.filter(2.0, 2 / 60.0)

        assertTrue(abs(t1 - t0) < 5.0)
        assertTrue(abs(t2 - t1) < 5.0)
        assertTrue(t1 in 0.0..360.0)
    }

    @Test
    fun stepInputHasAcceptableLag() {
        val filter = OneEuroFilter(minCutoff = 1.0, beta = 0.007, dCutoff = 1.0)
        val before = filter.filter(0.0, 0.0)
        val during = filter.filter(10.0, 0.5)
        val after = filter.filter(10.0, 2.0)

        assertTrue(before < 2.0)
        assertTrue(during in 2.0..10.0)
        assertTrue(after > 8.0)
    }

    private fun noisyAzimuthSignal(): List<Double> {
        val base = 45.0
        return (0 until 120).map { index ->
            base + kotlin.math.sin(index / 6.0) * 4.0 + (index % 3 - 1) * 1.5
        }
    }

    private fun variance(values: List<Double>): Double {
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }
}
