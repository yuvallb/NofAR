package com.nofar.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nofar.core.database.model.RegionEntityCoverageEntity

@Dao
interface RegionEntityCoverageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coverage: RegionEntityCoverageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(coverage: List<RegionEntityCoverageEntity>): List<Long>

    @Query("DELETE FROM region_entity_coverage WHERE region_id = :regionId")
    suspend fun deleteForRegion(regionId: String): Int

    @Query(
        """
        SELECT entity_id FROM region_entity_coverage
        WHERE region_id = :regionId
        """
    )
    suspend fun getEntityIdsForRegion(regionId: String): List<String>

    @Query(
        """
        SELECT entity_id, display_name FROM region_entity_coverage
        WHERE region_id = :regionId
        """
    )
    suspend fun getDisplayNamesForRegion(regionId: String): List<RegionEntityDisplayName>

    @Query(
        """
        SELECT entity_id FROM region_entity_coverage
        WHERE entity_id = :entityId
        """
    )
    suspend fun getRegionIdsForEntity(entityId: String): List<String>

    @Query(
        """
        SELECT entity_id FROM region_entity_coverage
        WHERE region_id = :regionId
        AND entity_id NOT IN (
            SELECT entity_id FROM region_entity_coverage WHERE region_id != :regionId
        )
        """
    )
    suspend fun findEntitiesExclusiveToRegion(regionId: String): List<String>
}
