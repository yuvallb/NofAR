package com.nofar.core.visibility

import android.util.Log
import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.data.repository.GeoEntityRepository
import com.nofar.core.database.dao.TileCoverageDao
import com.nofar.core.model.AppConfig
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import com.nofar.core.model.ResolutionLevel
import com.nofar.core.model.UserLocation
import java.util.UUID
import javax.inject.Inject

class VisibilityUseCase
@Inject
constructor(
    private val geoEntityRepository: GeoEntityRepository,
    private val demTileRepository: DemTileRepository,
    private val tileCoverageDao: TileCoverageDao,
    private val visibilityEngine: VisibilityEngine,
    private val observerElevationResolver: ObserverElevationResolver
) : RegionVisibilityComputer {
    override suspend fun computeForRegion(region: Region, location: UserLocation): VisibilityResult =
        computeForRegion(region, location, AppConfig.defaultResolutionLevel)

    suspend fun computeForRegion(
        region: Region,
        location: UserLocation,
        resolutionLevel: ResolutionLevel
    ): VisibilityResult {
        val warnings = mutableSetOf<VisibilityWarning>()
        if (region.downloadStatus == DownloadStatus.PARTIAL) {
            warnings += VisibilityWarning.DEM_TILE_MISSING
        }

        val demReaders = openDemReaders(region.id, warnings)
        val sampler = DemElevationSampler(demReaders)
        val observerElevation =
            observerElevationResolver.resolve(
                location = location,
                demElevationM = sampler.elevationAt(location.latitude, location.longitude)
            )
        observerElevation.warning?.let { warnings += it }

        val candidates = queryCandidates(region, location, resolutionLevel, warnings)
        val request =
            VisibilityRequest(
                observerLat = location.latitude,
                observerLon = location.longitude,
                observerElevationM = observerElevation.elevationM,
                eyeHeightM = AppConfig.EYE_HEIGHT_METERS,
                regionId = region.id,
                radiusM = region.radiusM,
                resolutionLevel = resolutionLevel,
                demReaders = demReaders,
                candidates = candidates,
                rayStepM = AppConfig.VISIBILITY_RAY_STEP_METERS,
                warnings = warnings
            )

        return try {
            visibilityEngine.computeVisibleEntities(request)
        } finally {
            demReaders.values.forEach { reader ->
                runCatching { reader.close() }
            }
        }
    }

    private suspend fun queryCandidates(
        region: Region,
        location: UserLocation,
        resolutionLevel: ResolutionLevel,
        warnings: MutableSet<VisibilityWarning>
    ): List<VisibilityCandidate> {
        val entities =
            geoEntityRepository.queryWithinRadiusForRegion(
                regionId = region.id,
                lat = location.latitude,
                lon = location.longitude,
                radiusM = region.radiusM,
                resolutionLevel = resolutionLevel
            )

        if (entities.size > AppConfig.VISIBILITY_MAX_CANDIDATES) {
            Log.w(TAG, "R-Tree returned ${entities.size} candidates; capping at ${AppConfig.VISIBILITY_MAX_CANDIDATES}")
            warnings += VisibilityWarning.CANDIDATE_CAP_EXCEEDED
        }

        return entities
            .sortedBy { entity ->
                RegionBounds.haversineDistanceM(
                    location.latitude,
                    location.longitude,
                    entity.lat,
                    entity.lon
                )
            }
            .take(AppConfig.VISIBILITY_MAX_CANDIDATES)
            .map { entity -> entity.toCandidate(location) }
    }

    private suspend fun openDemReaders(
        regionId: UUID,
        warnings: MutableSet<VisibilityWarning>
    ): Map<String, DemTileReader> {
        val tileIds = tileCoverageDao.getTileIdsForRegion(regionId.toString())
        val readers = LinkedHashMap<String, DemTileReader>()
        for (tileId in tileIds) {
            val reader = demTileRepository.openReader(tileId)
            if (reader == null) {
                warnings += VisibilityWarning.DEM_TILE_MISSING
                continue
            }
            readers[tileId] = reader
        }
        return readers
    }

    private fun GeoEntity.toCandidate(location: UserLocation): VisibilityCandidate {
        val distanceM =
            RegionBounds.haversineDistanceM(
                location.latitude,
                location.longitude,
                lat,
                lon
            )
        val bearingDeg =
            GeoMath.initialBearingDeg(
                location.latitude,
                location.longitude,
                lat,
                lon
            )
        return VisibilityCandidate(
            entity = this,
            bearingDeg = bearingDeg,
            distanceM = distanceM
        )
    }

    companion object {
        private const val TAG = "VisibilityUseCase"
    }
}
