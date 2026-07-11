package com.nofar.core.data.prepare

import com.nofar.core.model.AppConfig

object DownloadPolicy {
    sealed interface GateResult {
        data object Proceed : GateResult

        data class Blocked(val message: String) : GateResult

        data object CellularWarning : GateResult
    }

    fun evaluateStart(
        networkAvailable: Boolean,
        wifiOnlyDownloads: Boolean,
        onCellularNetwork: Boolean,
        estimateBytes: Long
    ): GateResult = when {
        !networkAvailable ->
            GateResult.Blocked(
                "No network connection. Connect to Wi-Fi or mobile data to download."
            )
        wifiOnlyDownloads && onCellularNetwork ->
            GateResult.Blocked(
                "Wi-Fi only downloads are enabled. Connect to Wi-Fi to continue."
            )
        onCellularNetwork && estimateBytes > AppConfig.CELLULAR_DOWNLOAD_WARNING_BYTES ->
            GateResult.CellularWarning
        else -> GateResult.Proceed
    }
}
