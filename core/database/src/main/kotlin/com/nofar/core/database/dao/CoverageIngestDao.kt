package com.nofar.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nofar.core.database.model.CoverageEntityEntity
import com.nofar.core.database.model.GeoEntityElevationMerge
import com.nofar.core.database.model.GeoEntityEntity

@Dao
abstract class CoverageIngestDao {
    @Query("SELECT * FROM geo_entity WHERE id = :osmId LIMIT 1")
    protected abstract suspend fun getByOsmId(osmId: String): GeoEntityEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertEntity(entity: GeoEntityEntity): Long

    @Query(
        """
        UPDATE geo_entity SET
            osm_type = :osmType,
            name = :name,
            type = :type,
            lat = :lat,
            lon = :lon,
            elevation = :elevation,
            elevation_source = :elevationSource,
            last_seen_at = :lastSeenAt,
            footprint_radius_m = :footprintRadiusM
        WHERE id = :osmId
        """
    )
    protected abstract suspend fun updateByOsmId(
        osmId: String,
        osmType: String,
        name: String,
        type: String,
        lat: Double,
        lon: Double,
        elevation: Int?,
        elevationSource: String?,
        lastSeenAt: Long,
        footprintRadiusM: Double?
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCoverage(coverage: CoverageEntityEntity): Long

    @Transaction
    open suspend fun upsertAndLinkEntity(coverageSetId: String, entity: GeoEntityEntity, displayName: String) {
        val existing = getByOsmId(entity.id)
        if (existing == null) {
            val rowId = insertEntity(entity)
            if (rowId == -1L) {
                val raced = getByOsmId(entity.id)
                    ?: error("geo_entity insert ignored but row missing for ${entity.id}")
                updateExisting(raced, entity)
            }
        } else {
            updateExisting(existing, entity)
        }
        insertCoverage(
            CoverageEntityEntity(
                coverageSetId = coverageSetId,
                entityId = entity.id,
                displayName = displayName
            )
        )
    }

    private suspend fun updateExisting(existing: GeoEntityEntity, entity: GeoEntityEntity) {
        val (elevation, elevationSource) =
            GeoEntityElevationMerge.resolve(
                incomingElevation = entity.elevation,
                incomingSource = entity.elevationSource,
                existingElevation = existing.elevation,
                existingSource = existing.elevationSource
            )
        updateByOsmId(
            osmId = entity.id,
            osmType = entity.osmType,
            name = entity.name,
            type = entity.type,
            lat = entity.lat,
            lon = entity.lon,
            elevation = elevation,
            elevationSource = elevationSource,
            lastSeenAt = entity.lastSeenAt,
            footprintRadiusM = entity.footprintRadiusM
        )
    }
}
