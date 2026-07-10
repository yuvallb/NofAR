package com.nofar.feature.prepare

import com.nofar.core.model.AppConfig

internal object PrepareDownloadPolicy {
    enum class BlockedReason {
        NO_NETWORK,
        WIFI_ONLY
    }

    sealed interface GateResult {
        data object Proceed : GateResult

        data class Blocked(val reason: BlockedReason) : GateResult

        data object CellularWarning : GateResult
    }

    fun evaluateStart(
        networkAvailable: Boolean,
        wifiOnlyDownloads: Boolean,
        onCellularNetwork: Boolean,
        estimateBytes: Long
    ): GateResult = when {
        !networkAvailable -> GateResult.Blocked(BlockedReason.NO_NETWORK)
        wifiOnlyDownloads && onCellularNetwork -> GateResult.Blocked(BlockedReason.WIFI_ONLY)
        onCellularNetwork && estimateBytes > AppConfig.CELLULAR_DOWNLOAD_WARNING_BYTES ->
            GateResult.CellularWarning
        else -> GateResult.Proceed
    }
}
