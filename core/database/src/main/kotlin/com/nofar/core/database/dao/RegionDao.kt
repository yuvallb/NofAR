package com.nofar.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nofar.core.database.model.RegionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RegionDao {
    @Query("SELECT * FROM region ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<RegionEntity>>

    @Query("SELECT * FROM region WHERE id = :regionId LIMIT 1")
    suspend fun getById(regionId: String): RegionEntity?

    @Query("SELECT * FROM region ORDER BY updated_at DESC")
    suspend fun getAll(): List<RegionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(region: RegionEntity): Long

    @Update
    suspend fun update(region: RegionEntity): Int

    @Delete
    suspend fun delete(region: RegionEntity): Int

    @Query("DELETE FROM region WHERE id = :regionId")
    suspend fun deleteById(regionId: String): Int

    @Query(
        """
        UPDATE region SET
            name = :name,
            updated_at = :updatedAt
        WHERE id = :regionId
        """
    )
    suspend fun updateName(regionId: String, name: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE region SET
            download_status = :status,
            download_progress_pct = :progressPct,
            updated_at = :updatedAt
        WHERE id = :regionId
        """
    )
    suspend fun updateDownloadStatus(regionId: String, status: String, progressPct: Int, updatedAt: Long): Int

    @Query(
        """
        SELECT * FROM region
        WHERE :lat BETWEEN min_lat AND max_lat
          AND :lon BETWEEN min_lon AND max_lon
        """
    )
    suspend fun getCandidatesContainingPoint(lat: Double, lon: Double): List<RegionEntity>
}
