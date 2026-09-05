@file:Suppress("ReturnCount", "LoopWithTooManyJumpStatements")

package com.nofar.core.data.prepare

import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.data.repository.GeoEntityRepository
import com.nofar.core.model.DemTileId
import com.nofar.core.model.ElevationSource
import com.nofar.core.model.GeoEntity
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Fills [GeoEntity.elevation] from converted DEM rasters when OSM did not supply `ele`
 * (Requirements §3.2 / Phase 2 AC-2.10).
 */
class MissingEntityElevationFiller
@Inject
constructor(
    private val geoEntityRepository: GeoEntityRepository,
    private val demTileRepository: DemTileRepository
) {
    data class Result(val attempted: Int, val filled: Int, val skippedExisting: Int, val failed: Int)

    /**
     * @param refreshDemSamples when true (Prepare post-process), re-sample entities that already
     * have [ElevationSource.DEM_SAMPLE] so a re-download after a DEM converter fix refreshes
     * values. When false (Home/Explore repair), only entities with null elevation are filled.
     */
    suspend fun fill(
        entityIds: List<String>,
        refreshDemSamples: Boolean = false,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): Result {
        val total = entityIds.size
        val readers = mutableMapOf<String, DemTileReader>()
        var attempted = 0
        var filled = 0
        var skippedExisting = 0
        var failed = 0

        try {
            for ((index, entityId) in entityIds.withIndex()) {
                onProgress(index + 1, total)
                val entity = geoEntityRepository.getById(entityId) ?: continue
                if (!shouldSample(entity, refreshDemSamples)) {
                    skippedExisting += 1
                    continue
                }
                attempted += 1
                val sampled = sampleElevation(entity, readers)
                if (sampled == null) {
                    failed += 1
                    continue
                }
                geoEntityRepository.upsert(
                    entity.copy(
                        elevation = sampled.roundToInt(),
                        elevationSource = ElevationSource.DEM_SAMPLE,
                        lastSeenAt = Instant.now()
                    )
                )
                filled += 1
            }
        } finally {
            readers.values.forEach(DemTileReader::close)
        }

        return Result(
            attempted = attempted,
            filled = filled,
            skippedExisting = skippedExisting,
            failed = failed
        )
    }

    private fun shouldSample(entity: GeoEntity, refreshDemSamples: Boolean): Boolean = when {
        entity.elevationSource == ElevationSource.OSM_TAG && entity.elevation != null -> false
        entity.elevation == null -> true
        refreshDemSamples && entity.elevationSource == ElevationSource.DEM_SAMPLE -> true
        else -> false
    }

    private fun sampleElevation(entity: GeoEntity, readers: MutableMap<String, DemTileReader>): Float? {
        if (entity.type == com.nofar.core.model.GeoEntityType.PEAK) {
            return samplePeakElevation(entity, readers)
        }
        val (tileLat, tileLon) = DemTileId.coordinatesForPoint(entity.lat, entity.lon)
        val tileId = DemTileId.fromCoordinates(tileLat, tileLon)
        val reader =
            readers[tileId] ?: demTileRepository.openReader(tileId)?.also { opened ->
                readers[tileId] = opened
            } ?: return null
        return reader.elevationAt(entity.lat, entity.lon)
    }

    /**
     * Peak elevations use the max of a 3×3 window of DEM samples around the peak pixel
     * (neighboring raster pixels, not neighboring 1° cells).
     */
    private fun samplePeakElevation(entity: GeoEntity, readers: MutableMap<String, DemTileReader>): Float? {
        val (tileLat, tileLon) = DemTileId.coordinatesForPoint(entity.lat, entity.lon)
        val tileId = DemTileId.fromCoordinates(tileLat, tileLon)
        val reader =
            readers[tileId] ?: demTileRepository.openReader(tileId)?.also { opened ->
                readers[tileId] = opened
            } ?: return null

        val degPerPixelX = 1.0 / reader.width
        val degPerPixelY = 1.0 / reader.height
        var best: Float? = null
        for (dy in -1..1) {
            for (dx in -1..1) {
                val sampleLat = entity.lat + dy * degPerPixelY
                val sampleLon = entity.lon + dx * degPerPixelX
                val sample = reader.elevationAt(sampleLat, sampleLon) ?: continue
                if (sample.isFinite() && (best == null || sample > best!!)) {
                    best = sample
                }
            }
        }
        // Fall back to single-pixel if neighbors fall outside the tile edge.
        return best ?: reader.elevationAt(entity.lat, entity.lon)
    }
}
