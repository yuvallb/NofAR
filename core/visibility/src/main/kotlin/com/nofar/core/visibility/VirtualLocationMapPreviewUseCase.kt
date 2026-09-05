package com.nofar.core.visibility

import android.util.Log
import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.data.dem.RegionDemTileResolver
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.TileCoverageDao
import com.nofar.core.model.AppConfig
import com.nofar.core.model.Region
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
    private val tileCoverageDao: TileCoverageDao,
    private val demTileDao: DemTileDao,
    private val observerElevationResolver: ObserverElevationResolver,
    private val terrainViewshedComputer: TerrainViewshedComputer
) {
    suspend fun compute(
        regions: List<Region>,
        clipRegions: List<Region>,
        observerLat: Double,
        observerLon: Double
    ): MapVisibilityPreview? {
        if (regions.isEmpty() || clipRegions.isEmpty()) return null
        val location =
            UserLocation(
                latitude = observerLat,
                longitude = observerLon,
                altitudeMeters = null,
                accuracyMeters = AppConfig.VIRTUAL_OBSERVER_ACCURACY_METERS,
                timestampMillis = System.currentTimeMillis()
            )
        val readers = openDemReaders(regions)
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
                    clipRegions = clipRegions,
                    sampler = sampler,
                    isCancelled = { job == null || !job.isActive }
                )
            } finally {
                readers.values.forEach { reader -> runCatching { reader.close() } }
            }
        }
    }

    private suspend fun openDemReaders(regions: List<Region>): Map<String, DemTileReader> {
        val tileIds = LinkedHashSet<String>()
        for (region in regions) {
            tileIds +=
                RegionDemTileResolver.resolveTileIds(
                    region = region,
                    tileCoverageDao = tileCoverageDao,
                    demTileDao = demTileDao,
                    tileReadable = demTileRepository::isBinReadable
                )
        }
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
