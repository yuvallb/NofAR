package com.nofar.core.sensors.filter

import kotlin.math.PI
import kotlin.math.abs

/**
 * 1€ filter from Casiez et al. (CHI 2012) — Apache 2.0 compatible implementation.
 */
class OneEuroFilter(private val minCutoff: Double, private val beta: Double, private val dCutoff: Double = 1.0) {
    private var xPrev: Double? = null
    private var dxPrev: Double? = null
    private var tPrev: Double? = null

    fun filter(value: Double, timestampSeconds: Double): Double {
        val tPrevLocal = tPrev
        if (tPrevLocal == null) {
            xPrev = value
            dxPrev = 0.0
            tPrev = timestampSeconds
            return value
        }

        val dt = (timestampSeconds - tPrevLocal).coerceAtLeast(1e-6)
        val dx = (value - (xPrev ?: value)) / dt
        val edx = lowPass(dx, dxPrev ?: 0.0, alpha(dCutoff, dt))
        val cutoff = minCutoff + beta * abs(edx)
        val filtered = lowPass(value, xPrev ?: value, alpha(cutoff, dt))

        xPrev = filtered
        dxPrev = edx
        tPrev = timestampSeconds
        return filtered
    }

    fun reset() {
        xPrev = null
        dxPrev = null
        tPrev = null
    }

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    private fun lowPass(value: Double, prev: Double, alpha: Double): Double = alpha * value + (1.0 - alpha) * prev
}

/**
 * One Euro filter for angular values with ±180° wrap handling.
 */
class AngularOneEuroFilter(minCutoff: Double, beta: Double, dCutoff: Double = 1.0) {
    private val euroFilter = OneEuroFilter(minCutoff, beta, dCutoff)
    private var lastUnwrapped: Double? = null

    fun filter(degrees: Double, timestampSeconds: Double): Double {
        val unwrapped = unwrap(degrees, lastUnwrapped)
        val filtered = euroFilter.filter(unwrapped, timestampSeconds)
        lastUnwrapped = filtered
        return normalizeDegrees(filtered)
    }

    fun reset() {
        euroFilter.reset()
        lastUnwrapped = null
    }

    private fun unwrap(degrees: Double, previous: Double?): Double {
        if (previous == null) return degrees
        var delta = degrees - normalizeDegrees(previous)
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return previous + delta
    }

    companion object {
        fun normalizeDegrees(degrees: Double): Double {
            var normalized = degrees % 360.0
            if (normalized < 0) normalized += 360.0
            return normalized
        }
    }
}
