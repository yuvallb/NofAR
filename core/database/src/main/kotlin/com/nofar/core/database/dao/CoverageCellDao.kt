package com.nofar.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nofar.core.database.model.CoverageCellEntity

@Dao
interface CoverageCellDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(coverage: CoverageCellEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(coverage: List<CoverageCellEntity>): List<Long>

    @Query("DELETE FROM coverage_cell WHERE coverage_set_id = :coverageSetId")
    suspend fun deleteForCoverageSet(coverageSetId: String): Int

    @Query("SELECT cell_id FROM coverage_cell WHERE coverage_set_id = :coverageSetId")
    suspend fun getCellIdsForCoverageSet(coverageSetId: String): List<String>

    @Query("SELECT coverage_set_id FROM coverage_cell WHERE cell_id = :cellId")
    suspend fun getCoverageSetIdsForCell(cellId: String): List<String>

    @Query(
        """
        SELECT DISTINCT cell_id FROM coverage_cell
        WHERE coverage_set_id IN (:coverageSetIds)
        """
    )
    suspend fun getCellIdsForCoverageSets(coverageSetIds: List<String>): List<String>

    @Query("DELETE FROM coverage_cell WHERE coverage_set_id = :coverageSetId AND cell_id = :cellId")
    suspend fun deleteForCoverageSetAndCell(coverageSetId: String, cellId: String): Int

    @Query("DELETE FROM coverage_cell WHERE cell_id = :cellId")
    suspend fun deleteForCell(cellId: String): Int
}
