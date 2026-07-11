package com.nofar.core.database

import androidx.sqlite.db.SimpleSQLiteQuery
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.database.dao.RegionEntityCoverageDao
import com.nofar.core.database.model.GeoEntityEntity
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.RegionBounds
import com.nofar.core.model.ResolutionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeoEntitySpatialQuery(
    private val database: NofARDatabase,
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
    ): List<GeoEntityEntity> =
        queryWithinRadiusInternal(
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
            val centerLat = regionCenterLat
            val centerLon = regionCenterLon
            val radius = regionRadiusM
            if (centerLat != null && centerLon != null && radius != null) {
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
            return emptyList()
        }

        val box = RegionBounds.boundingBox(lat, lon, radiusM)
        val rowIds = queryRowIdsWithinBoundingBox(box.minLat, box.maxLat, box.minLon, box.maxLon)
        return if (rowIds.isEmpty()) {
            emptyList()
        } else {
            geoEntityDao
                .getByRowIds(rowIds)
                .filter { entity ->
                    (regionEntityIds == null || entity.id in regionEntityIds) &&
                        RegionBounds.haversineDistanceM(lat, lon, entity.lat, entity.lon) <= radiusM &&
                        GeoEntityType.valueOf(entity.type).matchesResolution(resolutionLevel)
                }
        }
    }

    private suspend fun queryRowIdsWithinBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double
    ): List<Long> = withContext(Dispatchers.IO) {
        val sql =
            """
                SELECT g.row_id FROM geo_entity AS g
                INNER JOIN geo_entity_rtree AS r ON g.row_id = r.row_id
                WHERE r.max_lat >= ? AND r.min_lat <= ?
                  AND r.max_lon >= ? AND r.min_lon <= ?
            """.trimIndent()
        val args = arrayOf(minLat, maxLat, minLon, maxLon)
        database.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql, args)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getLong(0))
                }
            }
        }
    }
}
