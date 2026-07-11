package com.nofar.core.database

import androidx.sqlite.db.SimpleSQLiteQuery
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.database.dao.GeoEntitySpatialDao
import com.nofar.core.database.dao.RegionEntityCoverageDao
import com.nofar.core.database.model.GeoEntityEntity
import com.nofar.core.model.BoundingBox
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.RegionBounds
import com.nofar.core.model.ResolutionLevel

class GeoEntitySpatialQuery(
    private val geoEntitySpatialDao: GeoEntitySpatialDao,
    private val geoEntityDao: GeoEntityDao,
    private val regionEntityCoverageDao: RegionEntityCoverageDao
) {
    suspend fun queryWithinRadius(
        lat: Double,
        lon: Double,
        radiusM: Double,
        resolutionLevel: ResolutionLevel
    ): List<GeoEntityEntity> = queryWithinRadiusInternal(
        regionId = null,
        regionCenterLat = null,
        regionCenterLon = null,
        regionRadiusM = null,
        lat = lat,
        lon = lon,
        radiusM = radiusM,
        resolutionLevel = resolutionLevel
    )

    suspend fun queryWithinRadiusForRegion(
        regionId: String,
        regionCenterLat: Double,
        regionCenterLon: Double,
        regionRadiusM: Double,
        lat: Double,
        lon: Double,
        radiusM: Double,
        resolutionLevel: ResolutionLevel
    ): List<GeoEntityEntity> = queryWithinRadiusInternal(
        regionId = regionId,
        regionCenterLat = regionCenterLat,
        regionCenterLon = regionCenterLon,
        regionRadiusM = regionRadiusM,
        lat = lat,
        lon = lon,
        radiusM = radiusM,
        resolutionLevel = resolutionLevel
    )

    private suspend fun queryWithinRadiusInternal(
        regionId: String?,
        regionCenterLat: Double?,
        regionCenterLon: Double?,
        regionRadiusM: Double?,
        lat: Double,
        lon: Double,
        radiusM: Double,
        resolutionLevel: ResolutionLevel
    ): List<GeoEntityEntity> {
        val regionEntityIds = regionId?.let { regionEntityCoverageDao.getEntityIdsForRegion(it).toSet() }
        if (regionId != null && regionEntityIds.isNullOrEmpty()) {
            return queryWithoutCoverageFallback(
                regionCenterLat = regionCenterLat,
                regionCenterLon = regionCenterLon,
                regionRadiusM = regionRadiusM,
                lat = lat,
                lon = lon,
                radiusM = radiusM,
                resolutionLevel = resolutionLevel
            )
        }

        val box = RegionBounds.boundingBox(lat, lon, radiusM)
        val entities = loadEntitiesInBoundingBox(box, regionEntityIds)
        return entities.filter { entity ->
            RegionBounds.haversineDistanceM(lat, lon, entity.lat, entity.lon) <= radiusM &&
                GeoEntityType.fromStoredName(entity.type)?.matchesResolution(resolutionLevel) == true
        }
    }

    private suspend fun loadEntitiesInBoundingBox(
        box: BoundingBox,
        regionEntityIds: Set<String>?
    ): List<GeoEntityEntity> {
        val rowIds =
            runCatching {
                geoEntitySpatialDao.queryLongs(
                    SimpleSQLiteQuery(
                        """
                            SELECT g.row_id FROM geo_entity AS g
                            INNER JOIN geo_entity_rtree AS r ON g.row_id = r.row_id
                            WHERE r.max_lat >= ? AND r.min_lat <= ?
                              AND r.max_lon >= ? AND r.min_lon <= ?
                        """.trimIndent(),
                        arrayOf(box.minLat, box.maxLat, box.minLon, box.maxLon)
                    )
                )
            }.getOrElse { emptyList() }
        val candidates =
            when {
                rowIds.isNotEmpty() -> geoEntityDao.getByRowIds(rowIds)
                !regionEntityIds.isNullOrEmpty() -> geoEntityDao.getByOsmIds(regionEntityIds.toList())
                else -> geoEntityDao.getInBoundingBox(box.minLat, box.maxLat, box.minLon, box.maxLon)
            }
        return if (regionEntityIds == null) {
            candidates
        } else {
            candidates.filter { entity -> entity.id in regionEntityIds }
        }
    }

    private suspend fun queryWithoutCoverageFallback(
        regionCenterLat: Double?,
        regionCenterLon: Double?,
        regionRadiusM: Double?,
        lat: Double,
        lon: Double,
        radiusM: Double,
        resolutionLevel: ResolutionLevel
    ): List<GeoEntityEntity> {
        val centerLat = regionCenterLat
        val centerLon = regionCenterLon
        val radius = regionRadiusM
        if (centerLat == null || centerLon == null || radius == null) {
            return emptyList()
        }
        return queryWithinRadiusInternal(
            regionId = null,
            regionCenterLat = null,
            regionCenterLon = null,
            regionRadiusM = null,
            lat = lat,
            lon = lon,
            radiusM = radiusM,
            resolutionLevel = resolutionLevel
        ).filter { entity ->
            RegionBounds.haversineDistanceM(centerLat, centerLon, entity.lat, entity.lon) <= radius
        }
    }

    suspend fun backfillMissingRTreeEntries() {
        runCatching {
            geoEntitySpatialDao.executeStatement(
                SimpleSQLiteQuery(
                    """
                        INSERT INTO geo_entity_rtree(row_id, min_lat, max_lat, min_lon, max_lon)
                        SELECT g.row_id, g.lat, g.lat, g.lon, g.lon
                        FROM geo_entity AS g
                        LEFT JOIN geo_entity_rtree AS r ON g.row_id = r.row_id
                        WHERE r.row_id IS NULL
                    """.trimIndent()
                )
            )
        }
    }

    suspend fun clearRTree() {
        runCatching {
            geoEntitySpatialDao.executeStatement(SimpleSQLiteQuery("DELETE FROM geo_entity_rtree"))
        }
    }
}
