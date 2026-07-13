package com.nofar.core.visibility

import com.nofar.core.data.dem.RegionDemTileResolver
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.TileCoverageDao
import com.nofar.core.model.DemTileId
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

fun interface DemPointElevationSource {
    suspend fun elevationAt(lat: Double, lon: Double, region: Region?): Float?
}

/**
 * Samples ground elevation at a single lat/lon from the active region's downloaded DEM tiles.
 */
@Singleton
class PointDemElevationLookup
@Inject
constructor(
    private val demTileRepository: DemTileRepository,
    private val tileCoverageDao: TileCoverageDao,
    private val demTileDao: DemTileDao
) : DemPointElevationSource {
    private var cachedTileId: String? = null
    private var cachedLatKey: Int? = null
    private var cachedLonKey: Int? = null
    private var cachedElevationM: Float? = null

    override suspend fun elevationAt(lat: Double, lon: Double, region: Region?): Float? {
        val context = buildLookupContext(lat, lon, region)
        return when {
            context == null -> null
            context.isCacheHit -> cachedElevationM
            else -> sampleElevation(context)
        }
    }

    private suspend fun buildLookupContext(lat: Double, lon: Double, region: Region?): LookupContext? {
        val activeRegion = region
        return when {
            activeRegion == null -> null
            !RegionBounds.containsPoint(activeRegion, lat, lon) -> null
            else -> lookupContextForTile(activeRegion, lat, lon)
        }
    }

    private suspend fun lookupContextForTile(region: Region, lat: Double, lon: Double): LookupContext? {
        val (tileLat, tileLon) = DemTileId.coordinatesForPoint(lat, lon)
        val tileId = DemTileId.fromCoordinates(tileLat, tileLon)
        val regionTileIds =
            RegionDemTileResolver.resolveTileIds(
                region = region,
                tileCoverageDao = tileCoverageDao,
                demTileDao = demTileDao,
                tileReadable = demTileRepository::isBinReadable
            )
        if (tileId !in regionTileIds) return null

        val latKey = (lat * 1_000).roundToInt()
        val lonKey = (lon * 1_000).roundToInt()
        val isCacheHit = tileId == cachedTileId && latKey == cachedLatKey && lonKey == cachedLonKey
        return LookupContext(
            lat = lat,
            lon = lon,
            tileId = tileId,
            latKey = latKey,
            lonKey = lonKey,
            isCacheHit = isCacheHit
        )
    }

    private suspend fun sampleElevation(context: LookupContext): Float? {
        val reader = demTileRepository.openReader(context.tileId) ?: return null
        return try {
            reader.elevationAt(context.lat, context.lon)?.also { elevation ->
                cachedTileId = context.tileId
                cachedLatKey = context.latKey
                cachedLonKey = context.lonKey
                cachedElevationM = elevation
            }
        } finally {
            reader.close()
        }
    }

    private data class LookupContext(
        val lat: Double,
        val lon: Double,
        val tileId: String,
        val latKey: Int,
        val lonKey: Int,
        val isCacheHit: Boolean
    )
}
