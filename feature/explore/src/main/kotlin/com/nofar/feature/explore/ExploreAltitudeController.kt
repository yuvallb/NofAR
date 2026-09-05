package com.nofar.feature.explore

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
    private val activeCellIds: () -> Set<String>,
    private val isVirtual: Boolean = false
) {
    private var lastKnownGpsAltitudeM: Double? = null
    private var resolveJob: Job? = null

    fun scheduleResolve(location: UserLocation, cellIds: Set<String> = activeCellIds()) {
        if (!isVirtual && location.altitudeMeters != null) {
            lastKnownGpsAltitudeM = location.altitudeMeters
        }
        resolveJob?.cancel()
        resolveJob =
            scope.launch(Dispatchers.IO) {
                val reading =
                    displayAltitudeResolver.resolve(
                        location = location,
                        lastKnownGpsAltitudeM = if (isVirtual) null else lastKnownGpsAltitudeM,
                        cellIds = cellIds,
                        isVirtual = isVirtual
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
