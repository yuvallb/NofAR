package com.nofar.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nofar.core.database.model.GeoEntityEntity

@Dao
interface GeoEntityDao {
    @Query("SELECT * FROM geo_entity WHERE id = :osmId LIMIT 1")
    suspend fun getByOsmId(osmId: String): GeoEntityEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: GeoEntityEntity): Long

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
    suspend fun updateByOsmId(
        osmId: String,
        osmType: String,
        name: String,
        type: String,
        lat: Double,
        lon: Double,
        elevation: Double?,
        elevationSource: String?,
        lastSeenAt: Long,
        footprintRadiusM: Double?
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<GeoEntityEntity>): List<Long>

    @Query(
        """
        DELETE FROM geo_entity WHERE id IN (
            SELECT c.entity_id FROM region_entity_coverage c
            WHERE c.region_id = :regionId
            AND (
                SELECT COUNT(*) FROM region_entity_coverage c2
                WHERE c2.entity_id = c.entity_id
            ) = 1
        )
        """
    )
    suspend fun deleteEntitiesExclusiveToRegion(regionId: String): Int

    @Query(
        """
        DELETE FROM geo_entity WHERE id NOT IN (
            SELECT DISTINCT entity_id FROM region_entity_coverage
        )
        """
    )
    suspend fun deleteOrphans(): Int

    @Query("SELECT * FROM geo_entity WHERE row_id IN (:rowIds)")
    suspend fun getByRowIds(rowIds: List<Long>): List<GeoEntityEntity>

    @Query("SELECT * FROM geo_entity WHERE id IN (:osmIds)")
    suspend fun getByOsmIds(osmIds: List<String>): List<GeoEntityEntity>

    @Query(
        """
        SELECT * FROM geo_entity
        WHERE lat >= :minLat AND lat <= :maxLat
          AND lon >= :minLon AND lon <= :maxLon
        """
    )
    suspend fun getInBoundingBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<GeoEntityEntity>

    @Query("SELECT COUNT(*) FROM geo_entity")
    suspend fun countAll(): Int
}
