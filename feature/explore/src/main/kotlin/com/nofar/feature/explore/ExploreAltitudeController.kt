package com.nofar.feature.explore

import com.nofar.core.model.Region
import com.nofar.core.model.UserLocation
import com.nofar.core.visibility.DisplayAltitudeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ExploreAltitudeController(
    private val scope: CoroutineScope,
    private val displayAltitudeResolver: DisplayAltitudeResolver,
    private val uiState: MutableStateFlow<ExploreUiState>,
    private val activeRegion: () -> Region?
) {
    private var lastKnownGpsAltitudeM: Double? = null
    private var resolveJob: Job? = null

    fun scheduleResolve(location: UserLocation, region: Region? = null) {
        scheduleResolve(location, listOfNotNull(region))
    }

    fun scheduleResolve(location: UserLocation, regions: List<Region>) {
        if (location.altitudeMeters != null) {
            lastKnownGpsAltitudeM = location.altitudeMeters
        }
        resolveJob?.cancel()
        resolveJob =
            scope.launch(Dispatchers.IO) {
                val regionsToTry = regions.ifEmpty { listOfNotNull(activeRegion()) }
                val reading =
                    displayAltitudeResolver.resolve(
                        location = location,
                        lastKnownGpsAltitudeM = lastKnownGpsAltitudeM,
                        regions = regionsToTry
                    )
                uiState.update { it.copy(altitude = reading) }
            }
    }

    fun clearAltitude() {
        resolveJob?.cancel()
        resolveJob = null
        uiState.update { it.copy(altitude = null) }
    }
}
