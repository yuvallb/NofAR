package com.nofar.feature.explore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExploreZoomGesturesTest {
    @Test
    fun clampZoom_multipliesAndClampsToRange() {
        assertThat(clampZoom(current = 1f, scaleFactor = 2f, min = 1f, max = 5f)).isEqualTo(2f)
        assertThat(clampZoom(current = 4f, scaleFactor = 2f, min = 1f, max = 5f)).isEqualTo(5f)
        assertThat(clampZoom(current = 1f, scaleFactor = 0.5f, min = 1f, max = 5f)).isEqualTo(1f)
    }

    @Test
    fun formatZoom_usesCompactDisplay() {
        assertThat(formatZoom(1f)).isEqualTo("1x")
        assertThat(formatZoom(2.4f)).isEqualTo("2.4x")
        assertThat(formatZoom(10f)).isEqualTo("10x")
    }
}
