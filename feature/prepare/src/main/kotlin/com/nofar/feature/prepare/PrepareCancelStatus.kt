package com.nofar.feature.prepare

import com.nofar.core.data.prepare.PreparePhase
import com.nofar.core.data.prepare.PrepareProgress
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus

internal object PrepareCancelStatus {
    /**
     * While cancelling an in-progress download, keep PARTIAL when OSM/DEM work already produced
     * usable coverage; otherwise reset to NOT_DOWNLOADED.
     */
    fun resolve(
        region: CoverageSet?,
        progress: PrepareProgress?,
        hasTileCoverage: Boolean,
        liveEntityCount: Int
    ): DownloadStatus {
        when (region?.downloadStatus) {
            DownloadStatus.READY, DownloadStatus.PARTIAL -> return DownloadStatus.PARTIAL
            else -> Unit
        }
        val entityCount = maxOf(region?.entityCount ?: 0, liveEntityCount)
        val osmDone =
            progress != null &&
                (
                    progress.phase == PreparePhase.DEM ||
                        progress.phase == PreparePhase.POST_PROCESSING ||
                        progress.overallPercent >= OSM_DONE_PERCENT
                    )
        return if (entityCount > 0 || hasTileCoverage || osmDone) {
            DownloadStatus.PARTIAL
        } else {
            DownloadStatus.NOT_DOWNLOADED
        }
    }

    private const val OSM_DONE_PERCENT = 40
}
