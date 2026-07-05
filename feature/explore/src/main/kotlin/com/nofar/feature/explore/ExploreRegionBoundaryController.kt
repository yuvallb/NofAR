package com.nofar.feature.explore

import com.nofar.core.model.AppConfig
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import com.nofar.core.model.UserLocation
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class RegionBoundaryUiState(
    val insideActiveRegion: Boolean = true,
    val showRegionExitBanner: Boolean = false,
    val regionExitGraceSecondsRemaining: Int = 0,
    val showGraceExpiredDialog: Boolean = false
)

internal class ExploreRegionBoundaryController {
    private var regionExitStartedAtMillis: Long? = null
    private var graceTickerJob: Job? = null

    var insideActiveRegion: Boolean = true
        private set

    val isOutsideGraceActive: Boolean
        get() = regionExitStartedAtMillis != null

    fun onLocation(location: UserLocation, region: Region?): RegionBoundaryUiState {
        if (region == null) {
            insideActiveRegion = false
            regionExitStartedAtMillis = null
            return RegionBoundaryUiState(insideActiveRegion = false)
        }

        val distanceFromCenter =
            RegionBounds.haversineDistanceM(
                region.centerLat,
                region.centerLon,
                location.latitude,
                location.longitude
            )
        insideActiveRegion = distanceFromCenter <= region.radiusM

        return if (insideActiveRegion) {
            regionExitStartedAtMillis = null
            RegionBoundaryUiState(insideActiveRegion = true)
        } else {
            if (regionExitStartedAtMillis == null) {
                regionExitStartedAtMillis = System.currentTimeMillis()
            }
            RegionBoundaryUiState(insideActiveRegion = false)
        }
    }

    fun startGraceTicker(scope: CoroutineScope, onUpdate: (RegionBoundaryUiState) -> Unit) {
        if (regionExitStartedAtMillis == null) return
        graceTickerJob?.cancel()
        graceTickerJob =
            scope.launch {
                tickGracePeriod(onUpdate)
            }
    }

    fun stopGraceTicker() {
        graceTickerJob?.cancel()
        graceTickerJob = null
    }

    private suspend fun tickGracePeriod(onUpdate: (RegionBoundaryUiState) -> Unit) {
        while (true) {
            val startedAt = regionExitStartedAtMillis ?: return
            val graceMillis = AppConfig.exploreRegionExitGracePeriod.inWholeMilliseconds
            val elapsedMillis = System.currentTimeMillis() - startedAt
            val remainingMillis = (graceMillis - elapsedMillis).coerceAtLeast(0L)
            val remainingSeconds = (remainingMillis / 1_000L).toInt()

            val state =
                if (remainingMillis <= 0L) {
                    RegionBoundaryUiState(
                        insideActiveRegion = false,
                        showGraceExpiredDialog = true
                    )
                } else {
                    RegionBoundaryUiState(
                        insideActiveRegion = false,
                        showRegionExitBanner = true,
                        regionExitGraceSecondsRemaining = remainingSeconds
                    )
                }
            onUpdate(state)
            if (state.showGraceExpiredDialog) return
            delay(500.milliseconds)
        }
    }
}
