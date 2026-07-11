package com.nofar.core.data.prepare

import com.nofar.core.model.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {
    @Test
    fun wifiOnlyPreference_blocksCellularDownloadStart() {
        val result =
            DownloadPolicy.evaluateStart(
                networkAvailable = true,
                wifiOnlyDownloads = true,
                onCellularNetwork = true,
                estimateBytes = 10_000_000
            )

        assertTrue(result is DownloadPolicy.GateResult.Blocked)
        assertEquals(
            "Wi-Fi only downloads are enabled. Connect to Wi-Fi to continue.",
            (result as DownloadPolicy.GateResult.Blocked).message
        )
    }

    @Test
    fun wifiOnlyDisabled_allowsCellularWhenUnderWarningThreshold() {
        val result =
            DownloadPolicy.evaluateStart(
                networkAvailable = true,
                wifiOnlyDownloads = false,
                onCellularNetwork = true,
                estimateBytes = AppConfig.CELLULAR_DOWNLOAD_WARNING_BYTES
            )

        assertEquals(DownloadPolicy.GateResult.Proceed, result)
    }

    @Test
    fun cellularOverThreshold_showsWarning() {
        val result =
            DownloadPolicy.evaluateStart(
                networkAvailable = true,
                wifiOnlyDownloads = false,
                onCellularNetwork = true,
                estimateBytes = AppConfig.CELLULAR_DOWNLOAD_WARNING_BYTES + 1
            )

        assertEquals(DownloadPolicy.GateResult.CellularWarning, result)
    }
}
