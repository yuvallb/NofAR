package com.nofar.feature.prepare

import com.nofar.core.model.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepareDownloadPolicyTest {
    @Test
    fun wifiOnlyPreference_blocksCellularDownloadStart() {
        val result =
            PrepareDownloadPolicy.evaluateStart(
                networkAvailable = true,
                wifiOnlyDownloads = true,
                onCellularNetwork = true,
                estimateBytes = 10_000_000
            )

        assertTrue(result is PrepareDownloadPolicy.GateResult.Blocked)
        assertEquals(
            "Wi-Fi only downloads are enabled. Connect to Wi-Fi to continue.",
            (result as PrepareDownloadPolicy.GateResult.Blocked).message
        )
    }

    @Test
    fun wifiOnlyDisabled_allowsCellularWhenUnderWarningThreshold() {
        val result =
            PrepareDownloadPolicy.evaluateStart(
                networkAvailable = true,
                wifiOnlyDownloads = false,
                onCellularNetwork = true,
                estimateBytes = AppConfig.CELLULAR_DOWNLOAD_WARNING_BYTES
            )

        assertEquals(PrepareDownloadPolicy.GateResult.Proceed, result)
    }

    @Test
    fun cellularOverThreshold_showsWarning() {
        val result =
            PrepareDownloadPolicy.evaluateStart(
                networkAvailable = true,
                wifiOnlyDownloads = false,
                onCellularNetwork = true,
                estimateBytes = AppConfig.CELLULAR_DOWNLOAD_WARNING_BYTES + 1
            )

        assertEquals(PrepareDownloadPolicy.GateResult.CellularWarning, result)
    }
}
