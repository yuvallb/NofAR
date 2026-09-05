package com.nofar.core.visibility

import android.util.Log
import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.data.dem.RegionDemTileResolver
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.data.repository.GeoEntityRepository
import com.nofar.core.database.dao.TileCoverageDao
import com.nofar.core.model.AppConfig
import com.nofar.core.model.ContributingRegions
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.GeoEntityType
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
    private val visibilityEngine: VisibilityEngine,
    private val observerElevationResolver: ObserverElevationResolver,
    private val horizonProfileComputer: HorizonProfileComputer
) : RegionVisibilityComputer {
    override suspend fun computeForRegions(
        regions: List<Region>,
        location: UserLocation,
        computeHorizonProfile: Boolean
    ): VisibilityResult = computeForRegions(regions, location, AppConfig.defaultResolutionLevel, computeHorizonProfile)

    suspend fun computeForRegion(
        region: Region,
        location: UserLocation,
        resolutionLevel: ResolutionLevel = AppConfig.defaultResolutionLevel
    ): VisibilityResult = computeForRegions(listOf(region), location, resolutionLevel)

    suspend fun computeForRegions(
        regions: List<Region>,
        location: UserLocation,
        resolutionLevel: ResolutionLevel,
        computeHorizonProfile: Boolean = true
    ): VisibilityResult {
        if (regions.isEmpty()) {
            return VisibilityResult(entities = emptyList(), computationTimeMs = 0L, hereContext = HereContext())
        }

        val warnings = mutableSetOf<VisibilityWarning>()
        val demReaders = openDemReaders(regions, warnings)
        val sampler = DemElevationSampler(demReaders)
        val observerDemGroundM = sampler.elevationAt(location.latitude, location.longitude)
        val observerElevation =
            observerElevationResolver.resolve(
                location = location,
                demElevationM = observerDemGroundM
            )
        observerElevation.warning?.let { warnings += it }

        val candidateQuery = queryCandidates(regions, location, resolutionLevel, warnings)
        val hereContext =
            HereContextResolver.resolve(
                observerLat = location.latitude,
                observerLon = location.longitude,
                entities = candidateQuery.allEntities
            )
        val candidates = candidateQuery.candidates
        val collectionRadiusM = ContributingRegions.maxHorizonRadiusM(regions, location.latitude, location.longitude)
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
            val result =
                completeVisibilityResult(
                    visibilityResult = visibilityEngine.computeVisibleEntities(request),
                    location = location,
                    observerElevationM = observerElevation.elevationM,
                    observerDemGroundM = observerDemGroundM,
                    collectionRadiusM = collectionRadiusM,
                    computeHorizonProfile = computeHorizonProfile,
                    sampler = sampler,
                    hereContext = hereContext
                )
            warnIfOverBudget(result.computationTimeMs)
            result
        } finally {
            demReaders.values.forEach { reader ->
                runCatching { reader.close() }
            }
        }
    }

    private fun warnIfOverBudget(computationTimeMs: Long) {
        if (computationTimeMs > AppConfig.VISIBILITY_PASS_BUDGET_MS) {
            Log.w(
                TAG,
                "Visibility pass took ${computationTimeMs}ms " +
                    "(budget ${AppConfig.VISIBILITY_PASS_BUDGET_MS}ms)"
            )
        }
    }

    private fun completeVisibilityResult(
        visibilityResult: VisibilityResult,
        location: UserLocation,
        observerElevationM: Double,
        observerDemGroundM: Float?,
        collectionRadiusM: Double,
        computeHorizonProfile: Boolean,
        sampler: DemElevationSampler,
        hereContext: HereContext
    ): VisibilityResult {
        val observerEye =
            ObserverEyeAltitude.resolve(
                observerElevationM = observerElevationM,
                demGroundM = observerDemGroundM
            )
        val horizonProfile =
            buildHorizonProfile(
                horizonProfileComputer = horizonProfileComputer,
                computeHorizonProfile = computeHorizonProfile,
                observerLat = location.latitude,
                observerLon = location.longitude,
                observerEyeM = observerEye.eyeM,
                sampler = sampler,
                maxRadiusM = collectionRadiusM
            )
        return visibilityResult.copy(
            horizonProfile = horizonProfile,
            horizonEyeSource = horizonProfile?.let { observerEye.source },
            hereContext = hereContext
        )
    }

    private data class CandidateQueryResult(
        val allEntities: Collection<GeoEntity>,
        val candidates: List<VisibilityCandidate>
    )

    private suspend fun queryCandidates(
        regions: List<Region>,
        location: UserLocation,
        resolutionLevel: ResolutionLevel,
        warnings: MutableSet<VisibilityWarning>
    ): CandidateQueryResult {
        val entitiesById = LinkedHashMap<String, GeoEntity>()
        for (region in regions) {
            val collectionRadiusM = RegionBounds.dataCollectionRadiusM(region)
            val observerToCenterM =
                RegionBounds.haversineDistanceM(
                    location.latitude,
                    location.longitude,
                    region.centerLat,
                    region.centerLon
                )
            // Reach every point still inside the collection circle from the observer.
            val queryRadiusM = collectionRadiusM + observerToCenterM
            val entities =
                geoEntityRepository.queryWithinRadiusForRegion(
                    regionId = region.id,
                    regionCenterLat = region.centerLat,
                    regionCenterLon = region.centerLon,
                    regionRadiusM = collectionRadiusM,
                    lat = location.latitude,
                    lon = location.longitude,
                    radiusM = queryRadiusM,
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

        return CandidateQueryResult(
            allEntities = entitiesById.values,
            candidates =
            VisibilityCandidateSelector
                .select(
                    entities = entitiesById.values,
                    location = location,
                    maxCandidates = AppConfig.VISIBILITY_MAX_CANDIDATES,
                    peakBudget = AppConfig.PEAK_CANDIDATE_BUDGET
                ).map { entity -> entity.toCandidate(location) }
        )
    }

    private suspend fun openDemReaders(
        regions: List<Region>,
        warnings: MutableSet<VisibilityWarning>
    ): Map<String, DemTileReader> {
        val expectedTileIds = LinkedHashSet<String>()
        for (region in regions) {
            expectedTileIds +=
                RegionDemTileResolver.resolveExpectedTileIds(
                    region = region,
                    tileCoverageDao = tileCoverageDao
                )
        }
        val readers = LinkedHashMap<String, DemTileReader>()
        for (tileId in expectedTileIds) {
            demTileRepository.ensureRegisteredFromBin(tileId)
            val reader = demTileRepository.openReader(tileId)
            if (reader == null) {
                Log.w(TAG, "Skipping unreadable DEM tile $tileId")
                continue
            }
            readers[tileId] = reader
        }
        if (readers.size < expectedTileIds.size) {
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

/**
 * Caps visibility candidates: nearest peaks up to [peakBudget], then nearest non-peaks to fill
 * [maxCandidates].
 */
internal object VisibilityCandidateSelector {
    fun select(
        entities: Collection<GeoEntity>,
        location: UserLocation,
        maxCandidates: Int,
        peakBudget: Int,
        nearestPeakBudget: Int = AppConfig.PEAK_CANDIDATE_NEAREST_BUDGET,
        longRangePeakBudget: Int = AppConfig.PEAK_CANDIDATE_LONG_RANGE_BUDGET
    ): List<GeoEntity> {
        if (entities.size <= maxCandidates) {
            return entities.sortedBy { entity -> distanceM(location, entity) }
        }
        val byDistance = entities.sortedBy { entity -> distanceM(location, entity) }
        val peaks = byDistance.filter { it.type == GeoEntityType.PEAK }
        val places = byDistance.filter { it.type != GeoEntityType.PEAK }
        val nearestPeakCount = nearestPeakBudget.coerceAtMost(peakBudget.coerceAtMost(maxCandidates))
        val longRangePeakCount =
            longRangePeakBudget.coerceAtMost((peakBudget - nearestPeakCount).coerceAtLeast(0))
        val selectedNearestPeaks = peaks.take(nearestPeakCount)
        val selectedPeakIds = selectedNearestPeaks.map { it.id }.toSet()
        val longRangePeaks =
            peaks
                .filterNot { it.id in selectedPeakIds }
                .sortedWith(
                    compareByDescending<GeoEntity> { it.elevation ?: Int.MIN_VALUE }
                        .thenBy { entity -> distanceM(location, entity) }
                ).take(longRangePeakCount)
        val selectedPeaks = (selectedNearestPeaks + longRangePeaks).distinctBy { it.id }
        val remaining = (maxCandidates - selectedPeaks.size).coerceAtLeast(0)
        return selectedPeaks + places.take(remaining)
    }

    private fun distanceM(location: UserLocation, entity: GeoEntity): Double = RegionBounds.haversineDistanceM(
        location.latitude,
        location.longitude,
        entity.lat,
        entity.lon
    )
}

/**
 * Attaches (or skips) the skyline sweep for a pass. Extracted to file scope so the skip-when-disabled
 * behavior (H-P1-11) and the "profile is actually computed" invariant (H-P1-16) are unit-testable
 * without the full repository/DAO graph.
 */
internal fun buildHorizonProfile(
    horizonProfileComputer: HorizonProfileComputer,
    computeHorizonProfile: Boolean,
    observerLat: Double,
    observerLon: Double,
    observerEyeM: Double,
    sampler: DemSampler,
    maxRadiusM: Double
): HorizonProfile? = if (!computeHorizonProfile) {
    null
} else {
    horizonProfileComputer.sweep(
        observerLat = observerLat,
        observerLon = observerLon,
        observerEyeM = observerEyeM,
        sampler = sampler,
        maxRadiusM = maxRadiusM
    )
}
