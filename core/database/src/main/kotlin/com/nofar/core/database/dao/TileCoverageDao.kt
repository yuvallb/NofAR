package com.nofar.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nofar.core.database.model.TileCoverageEntity

@Dao
interface TileCoverageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(coverage: TileCoverageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(coverage: List<TileCoverageEntity>): List<Long>

    @Query("DELETE FROM tile_coverage WHERE region_id = :regionId")
    suspend fun deleteForRegion(regionId: String): Int

    @Query("SELECT tile_id FROM tile_coverage WHERE region_id = :regionId")
    suspend fun getTileIdsForRegion(regionId: String): List<String>
}
