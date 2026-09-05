package com.nofar.core.visibility

import android.util.Log
import com.nofar.core.data.dem.CoverageDemTileResolver
import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.model.AppConfig
import com.nofar.core.model.UserLocation
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job

@Singleton
class VirtualLocationMapPreviewUseCase
@Inject
constructor(
    private val demTileRepository: DemTileRepository,
    private val demTileDao: DemTileDao,
    private val observerElevationResolver: ObserverElevationResolver,
    private val terrainViewshedComputer: TerrainViewshedComputer
) {
    suspend fun compute(
        cellIds: Set<String>,
        clipCellIds: Set<String>,
        observerLat: Double,
        observerLon: Double
    ): MapVisibilityPreview? {
        if (cellIds.isEmpty() || clipCellIds.isEmpty()) return null
        val location =
            UserLocation(
                latitude = observerLat,
                longitude = observerLon,
                altitudeMeters = null,
                accuracyMeters = AppConfig.VIRTUAL_OBSERVER_ACCURACY_METERS,
                timestampMillis = System.currentTimeMillis()
            )
        val readers = openDemReaders(cellIds)
        return if (readers.isEmpty()) {
            null
        } else {
            try {
                val sampler = PreviewDemElevationSampler(readers)
                val demGroundM = sampler.elevationAt(observerLat, observerLon)
                val observerElevation = observerElevationResolver.resolve(location, demGroundM)
                val observerEye =
                    ObserverEyeAltitude.resolve(
                        observerElevationM = observerElevation.elevationM,
                        demGroundM = demGroundM
                    )
                val job = coroutineContext[Job]
                terrainViewshedComputer.compute(
                    observerLat = observerLat,
                    observerLon = observerLon,
                    observerEyeM = observerEye.eyeM,
                    clipCellIds = clipCellIds,
                    sampler = sampler,
                    isCancelled = { job == null || !job.isActive }
                )
            } finally {
                readers.values.forEach { reader -> runCatching { reader.close() } }
            }
        }
    }

    private suspend fun openDemReaders(cellIds: Set<String>): Map<String, DemTileReader> {
        val tileIds =
            CoverageDemTileResolver.resolveTileIds(
                cellIds = cellIds.toList(),
                demTileDao = demTileDao,
                tileReadable = demTileRepository::isBinReadable
            )
        val readers = LinkedHashMap<String, DemTileReader>()
        for (tileId in tileIds) {
            demTileRepository.ensureRegisteredFromBin(tileId)
            val reader = demTileRepository.openReader(tileId) ?: continue
            readers[tileId] = reader
        }
        if (readers.isEmpty() && tileIds.isNotEmpty()) {
            Log.w(TAG, "No readable DEM tiles for virtual location preview")
        }
        return readers
    }

    companion object {
        private const val TAG = "VirtualLocationMapPreview"
    }
}
