package com.nofar.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.nofar.core.database.model.CoverageSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoverageSetDao {
    @Query("SELECT * FROM coverage_set ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<CoverageSetEntity>>

    @Query("SELECT * FROM coverage_set WHERE id = :coverageSetId LIMIT 1")
    suspend fun getById(coverageSetId: String): CoverageSetEntity?

    @Query("SELECT * FROM coverage_set ORDER BY updated_at DESC")
    suspend fun getAll(): List<CoverageSetEntity>

    @Upsert
    suspend fun upsert(coverageSet: CoverageSetEntity): Long

    @Update
    suspend fun update(coverageSet: CoverageSetEntity): Int

    @Delete
    suspend fun delete(coverageSet: CoverageSetEntity): Int

    @Query("DELETE FROM coverage_set WHERE id = :coverageSetId")
    suspend fun deleteById(coverageSetId: String): Int

    @Query(
        """
        UPDATE coverage_set SET
            name = :name,
            updated_at = :updatedAt
        WHERE id = :coverageSetId
        """
    )
    suspend fun updateName(coverageSetId: String, name: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE coverage_set SET
            download_status = :status,
            download_progress_pct = :progressPct,
            osm_dataset_version = COALESCE(:osmDatasetVersion, osm_dataset_version),
            entity_count = COALESCE(:entityCount, entity_count),
            updated_at = :updatedAt
        WHERE id = :coverageSetId
        """
    )
    suspend fun updateDownloadStatus(
        coverageSetId: String,
        status: String,
        progressPct: Int,
        updatedAt: Long,
        osmDatasetVersion: Long? = null,
        entityCount: Int? = null
    ): Int
}
