package com.nofar.core.visibility

import android.util.Log
import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.data.dem.RegionDemTileResolver
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.data.repository.GeoEntityRepository
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.TileCoverageDao
import com.nofar.core.model.AppConfig
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import com.nofar.core.model.ResolutionLevel
import com.nofar.core.model.UserLocation
import javax.inject.Inject

class VisibilityUseCase
@Inject
constructor(
    private val geoEntityRepository: GeoEntityRepository,
    private val demTileRepository: DemTileRepository,
    private val tileCoverageDao: TileCoverageDao,
    private val demTileDao: DemTileDao,
    private val visibilityEngine: VisibilityEngine,
    private val observerElevationResolver: ObserverElevationResolver
) : RegionVisibilityComputer {
    override suspend fun computeForRegions(regions: List<Region>, location: UserLocation): VisibilityResult =
        computeForRegions(regions, location, AppConfig.defaultResolutionLevel)

    suspend fun computeForRegion(
        region: Region,
        location: UserLocation,
        resolutionLevel: ResolutionLevel = AppConfig.defaultResolutionLevel
    ): VisibilityResult = computeForRegions(listOf(region), location, resolutionLevel)

    suspend fun computeForRegions(
        regions: List<Region>,
        location: UserLocation,
        resolutionLevel: ResolutionLevel
    ): VisibilityResult {
        if (regions.isEmpty()) {
            return VisibilityResult(entities = emptyList(), computationTimeMs = 0L)
        }

        val warnings = mutableSetOf<VisibilityWarning>()
        val demReaders = openDemReaders(regions, warnings)
        val sampler = DemElevationSampler(demReaders)
        val observerElevation =
            observerElevationResolver.resolve(
                location = location,
                demElevationM = sampler.elevationAt(location.latitude, location.longitude)
            )
        observerElevation.warning?.let { warnings += it }

        val candidates = queryCandidates(regions, location, resolutionLevel, warnings)
        val collectionRadiusM = regions.maxOf { RegionBounds.dataCollectionRadiusM(it) }
        val request =
            VisibilityRequest(
                observerLat = location.latitude,
                observerLon = location.longitude,
                observerElevationM = observerElevation.elevationM,
                eyeHeightM = AppConfig.EYE_HEIGHT_METERS,
                regionId = regions.first().id,
                radiusM = collectionRadiusM,
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
        regions: List<Region>,
        location: UserLocation,
        resolutionLevel: ResolutionLevel,
        warnings: MutableSet<VisibilityWarning>
    ): List<VisibilityCandidate> {
        val entitiesById = LinkedHashMap<String, GeoEntity>()
        for (region in regions) {
            val collectionRadiusM = RegionBounds.dataCollectionRadiusM(region)
            val entities =
                geoEntityRepository.queryWithinRadiusForRegion(
                    regionId = region.id,
                    regionCenterLat = region.centerLat,
                    regionCenterLon = region.centerLon,
                    regionRadiusM = collectionRadiusM,
                    lat = region.centerLat,
                    lon = region.centerLon,
                    radiusM = collectionRadiusM,
                    resolutionLevel = resolutionLevel
                )
            entities.forEach { entity -> entitiesById.putIfAbsent(entity.id, entity) }
        }

        if (entitiesById.isEmpty()) {
            Log.w(
                TAG,
                "No visibility candidates for ${regions.size} region(s) at " +
                    "${location.latitude},${location.longitude}"
            )
        }

        if (entitiesById.size > AppConfig.VISIBILITY_MAX_CANDIDATES) {
            Log.w(
                TAG,
                "R-Tree returned ${entitiesById.size} candidates; capping at ${AppConfig.VISIBILITY_MAX_CANDIDATES}"
            )
            warnings += VisibilityWarning.CANDIDATE_CAP_EXCEEDED
        }

        return entitiesById.values
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
        regions: List<Region>,
        warnings: MutableSet<VisibilityWarning>
    ): Map<String, DemTileReader> {
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
            val reader = demTileRepository.openReader(tileId)
            if (reader == null) {
                Log.w(TAG, "Skipping unreadable DEM tile $tileId")
                continue
            }
            readers[tileId] = reader
        }
        if (readers.isEmpty() && tileIds.isNotEmpty()) {
            warnings += VisibilityWarning.DEM_TILE_MISSING
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
