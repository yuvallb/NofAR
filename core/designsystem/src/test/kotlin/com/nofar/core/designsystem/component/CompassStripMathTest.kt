package com.nofar.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassStripMathTest {
    @Test
    fun headingDelta_wrapsAcrossNorth() {
        assertEquals(10f, compassHeadingDeltaDeg(markDeg = 0f, bearingDeg = 350f), 0.001f)
        assertEquals(-10f, compassHeadingDeltaDeg(markDeg = 340f, bearingDeg = 350f), 0.001f)
    }

    @Test
    fun nearestUnwrappedMarkDeg_picksClosestCycle() {
        assertEquals(360f, nearestUnwrappedMarkDeg(markDeg = 0, bearingDeg = 350f), 0.001f)
        assertEquals(340f, nearestUnwrappedMarkDeg(markDeg = 340, bearingDeg = 350f), 0.001f)
    }

    @Test
    fun markCenterOffsetPx_mapsFovAcrossViewport() {
        val viewportWidthPx = 400f
        val horizontalFovDeg = 60f

        assertEquals(
            -113.333f,
            compassMarkCenterOffsetPx(
                markDeg = 20f,
                bearingDeg = 37f,
                viewportWidthPx = viewportWidthPx,
                horizontalFovDeg = horizontalFovDeg
            ),
            0.1f
        )
        assertEquals(
            20f,
            compassMarkCenterOffsetPx(
                markDeg = 40f,
                bearingDeg = 37f,
                viewportWidthPx = viewportWidthPx,
                horizontalFovDeg = horizontalFovDeg
            ),
            0.1f
        )
        assertEquals(
            200f,
            compassMarkCenterOffsetPx(
                markDeg = 67f,
                bearingDeg = 37f,
                viewportWidthPx = viewportWidthPx,
                horizontalFovDeg = horizontalFovDeg
            ),
            0.1f
        )
    }
}
