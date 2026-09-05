@file:Suppress("ReturnCount")

package com.nofar.core.database

import androidx.sqlite.db.SimpleSQLiteQuery
import com.nofar.core.database.dao.CoverageEntityDao
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.database.dao.GeoEntitySpatialDao
import com.nofar.core.database.model.GeoEntityEntity
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.GeoMathBounds
import com.nofar.core.model.ResolutionLevel

class GeoEntitySpatialQuery(
    private val geoEntitySpatialDao: GeoEntitySpatialDao,
    private val geoEntityDao: GeoEntityDao,
    private val coverageEntityDao: CoverageEntityDao
) {
    suspend fun queryWithinRadius(
        lat: Double,
        lon: Double,
        radiusM: Double,
        resolutionLevel: ResolutionLevel
    ): List<GeoEntityEntity> = queryWithinRadiusInternal(
        coverageSetId = null,
        lat = lat,
        lon = lon,
        radiusM = radiusM,
        resolutionLevel = resolutionLevel
    )

    suspend fun queryWithinRadiusForCoverageSet(
        coverageSetId: String,
        lat: Double,
        lon: Double,
        radiusM: Double,
        resolutionLevel: ResolutionLevel
    ): List<GeoEntityEntity> = queryWithinRadiusInternal(
        coverageSetId = coverageSetId,
        lat = lat,
        lon = lon,
        radiusM = radiusM,
        resolutionLevel = resolutionLevel
    )

    private suspend fun queryWithinRadiusInternal(
        coverageSetId: String?,
        lat: Double,
        lon: Double,
        radiusM: Double,
        resolutionLevel: ResolutionLevel
    ): List<GeoEntityEntity> {
        val box = GeoMathBounds.boundingBox(lat, lon, radiusM)
        val boundingBoxCandidates = loadEntitiesInBoundingBox(box)
        if (boundingBoxCandidates.isEmpty()) return emptyList()
        val coverageCandidates =
            if (coverageSetId == null) {
                boundingBoxCandidates
            } else {
                val coverageEntityIds =
                    coverageEntityDao.getEntityIdsForCoverageSet(coverageSetId).toSet()
                if (coverageEntityIds.isEmpty()) return emptyList()
                boundingBoxCandidates.filter { entity -> entity.id in coverageEntityIds }
            }
        val entities =
            coverageCandidates
                .filter { entity ->
                    GeoEntityType.fromStoredName(entity.type)?.matchesResolution(resolutionLevel) == true &&
                        (
                            coverageSetId != null ||
                                GeoMathBounds.haversineDistanceM(lat, lon, entity.lat, entity.lon) <= radiusM
                            )
                }
        if (coverageSetId == null || entities.isEmpty()) return entities
        val displayNamesByEntityId =
            coverageEntityDao.getDisplayNamesForCoverageSetAndEntities(
                coverageSetId = coverageSetId,
                entityIds = entities.map { it.id }
            ).associate { it.entityId to it.displayName }
        return entities.map { entity ->
            val displayName = displayNamesByEntityId[entity.id]
            if (!displayName.isNullOrBlank()) {
                entity.copy(name = displayName)
            } else {
                entity
            }
        }
    }

    private suspend fun loadEntitiesInBoundingBox(box: com.nofar.core.model.BoundingBox): List<GeoEntityEntity> {
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
        return if (rowIds.isNotEmpty()) {
            geoEntityDao.getByRowIds(rowIds)
        } else {
            geoEntityDao.getInBoundingBox(box.minLat, box.maxLat, box.minLon, box.maxLon)
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
