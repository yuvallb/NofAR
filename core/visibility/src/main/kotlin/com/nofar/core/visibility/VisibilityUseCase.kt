package com.nofar.core.visibility

import android.util.Log
import com.nofar.core.data.dem.CoverageDemTileResolver
import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.data.repository.CoverageSetRepository
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.data.repository.GeoEntityRepository
import com.nofar.core.model.AppConfig
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.GeoMathBounds
import com.nofar.core.model.ResolutionLevel
import com.nofar.core.model.UserLocation
import javax.inject.Inject

class VisibilityUseCase
@Inject
constructor(
    private val geoEntityRepository: GeoEntityRepository,
    private val demTileRepository: DemTileRepository,
    private val coverageSetRepository: CoverageSetRepository,
    private val visibilityEngine: VisibilityEngine,
    private val observerElevationResolver: ObserverElevationResolver,
    private val horizonProfileComputer: HorizonProfileComputer
) : CoverageVisibilityComputer {
    override suspend fun computeForCoverageSets(
        coverageSets: List<CoverageSet>,
        cellIds: Set<String>,
        location: UserLocation,
        computeHorizonProfile: Boolean
    ): VisibilityResult = computeForCoverageSets(
        coverageSets,
        cellIds,
        location,
        AppConfig.defaultResolutionLevel,
        computeHorizonProfile
    )

    suspend fun computeForCoverageSet(
        coverageSet: CoverageSet,
        cellIds: Set<String>,
        location: UserLocation,
        resolutionLevel: ResolutionLevel = AppConfig.defaultResolutionLevel
    ): VisibilityResult = computeForCoverageSets(listOf(coverageSet), cellIds, location, resolutionLevel)

    suspend fun computeForCoverageSets(
        coverageSets: List<CoverageSet>,
        cellIds: Set<String>,
        location: UserLocation,
        resolutionLevel: ResolutionLevel,
        computeHorizonProfile: Boolean = true
    ): VisibilityResult {
        if (coverageSets.isEmpty()) {
            return VisibilityResult(entities = emptyList(), computationTimeMs = 0L, hereContext = HereContext())
        }

        val warnings = mutableSetOf<VisibilityWarning>()
        val demReaders = openDemReaders(cellIds, warnings)
        val sampler = DemElevationSampler(demReaders)
        val observerDemGroundM = sampler.elevationAt(location.latitude, location.longitude)
        val observerElevation =
            observerElevationResolver.resolve(
                location = location,
                demElevationM = observerDemGroundM
            )
        observerElevation.warning?.let { warnings += it }

        val candidateQuery = queryCandidates(coverageSets, location, resolutionLevel, warnings)
        val hereContext =
            HereContextResolver.resolve(
                observerLat = location.latitude,
                observerLon = location.longitude,
                entities = candidateQuery.allEntities
            )
        val candidates = candidateQuery.candidates
        val collectionRadiusM = AppConfig.EXPLORE_ENTITY_QUERY_RADIUS_M
        val request =
            VisibilityRequest(
                observerLat = location.latitude,
                observerLon = location.longitude,
                observerElevationM = observerElevation.elevationM,
                eyeHeightM = AppConfig.EYE_HEIGHT_METERS,
                regionId = coverageSets.first().id,
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
        coverageSets: List<CoverageSet>,
        location: UserLocation,
        resolutionLevel: ResolutionLevel,
        warnings: MutableSet<VisibilityWarning>
    ): CandidateQueryResult {
        val entitiesById = LinkedHashMap<String, GeoEntity>()
        val queryRadiusM = AppConfig.EXPLORE_ENTITY_QUERY_RADIUS_M
        for (coverageSet in coverageSets) {
            val entities =
                geoEntityRepository.queryWithinRadiusForCoverageSet(
                    coverageSetId = coverageSet.id,
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
                "No visibility candidates for ${coverageSets.size} coverage set(s) at " +
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
        cellIds: Set<String>,
        warnings: MutableSet<VisibilityWarning>
    ): Map<String, DemTileReader> {
        val expectedTileIds = CoverageDemTileResolver.cellIdsToTileIds(cellIds.toList()).toSet()
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
            GeoMathBounds.haversineDistanceM(
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

    private fun distanceM(location: UserLocation, entity: GeoEntity): Double = GeoMathBounds.haversineDistanceM(
        location.latitude,
        location.longitude,
        entity.lat,
        entity.lon
    )
}

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
