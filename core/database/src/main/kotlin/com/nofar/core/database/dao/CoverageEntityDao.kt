package com.nofar.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nofar.core.database.model.CoverageEntityEntity

@Dao
interface CoverageEntityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coverage: CoverageEntityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(coverage: List<CoverageEntityEntity>): List<Long>

    @Query("DELETE FROM coverage_entity WHERE coverage_set_id = :coverageSetId")
    suspend fun deleteForCoverageSet(coverageSetId: String): Int

    @Query(
        """
        SELECT entity_id FROM coverage_entity
        WHERE coverage_set_id = :coverageSetId
        """
    )
    suspend fun getEntityIdsForCoverageSet(coverageSetId: String): List<String>

    @Query(
        """
        SELECT entity_id, display_name FROM coverage_entity
        WHERE coverage_set_id = :coverageSetId
        """
    )
    suspend fun getDisplayNamesForCoverageSet(coverageSetId: String): List<CoverageEntityDisplayName>

    @Query(
        """
        SELECT entity_id, display_name FROM coverage_entity
        WHERE coverage_set_id = :coverageSetId
          AND entity_id IN (:entityIds)
        """
    )
    suspend fun getDisplayNamesForCoverageSetAndEntities(
        coverageSetId: String,
        entityIds: List<String>
    ): List<CoverageEntityDisplayName>

    @Query(
        """
        SELECT coverage_set_id FROM coverage_entity
        WHERE entity_id = :entityId
        """
    )
    suspend fun getCoverageSetIdsForEntity(entityId: String): List<String>

    @Query(
        """
        SELECT entity_id FROM coverage_entity
        WHERE coverage_set_id = :coverageSetId
        AND entity_id NOT IN (
            SELECT entity_id FROM coverage_entity WHERE coverage_set_id != :coverageSetId
        )
        """
    )
    suspend fun findEntitiesExclusiveToCoverageSet(coverageSetId: String): List<String>
}

data class CoverageEntityDisplayName(
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "display_name") val displayName: String
)
