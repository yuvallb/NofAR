package com.nofar.feature.prepare

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VirtualLocationPickerMessagesTest {
    @Test
    fun outsideActiveRegion_mentionsPrepare() {
        assertThat(VirtualLocationPickerMessages.OUTSIDE_ACTIVE_REGION).contains("Prepare")
    }

    @Test
    fun helper_whenNoRegions_promptsPrepareDownload() {
        val message =
            VirtualLocationPickerMessages.helper(
                eligibleRegions = emptyList(),
                lat = null,
                lon = null,
                selectionValid = false,
                analyzingVisibility = false
            )
        assertThat(message).contains("Prepare")
    }
}
