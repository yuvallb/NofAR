package com.nofar.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nofar.core.database.model.DemTileEntity

@Dao
interface DemTileDao {
    @Query("SELECT * FROM dem_tile WHERE tile_id = :tileId LIMIT 1")
    suspend fun getById(tileId: String): DemTileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tile: DemTileEntity): Long

    @Query("UPDATE dem_tile SET ref_count = ref_count + 1 WHERE tile_id = :tileId")
    suspend fun incrementRefCount(tileId: String): Int

    @Query(
        """
        UPDATE dem_tile SET
            ref_count = MAX(ref_count - 1, 0),
            last_accessed_at = :lastAccessedAt
        WHERE tile_id = :tileId
        """
    )
    suspend fun decrementRefCount(tileId: String, lastAccessedAt: Long): Int

    @Query("SELECT * FROM dem_tile WHERE ref_count = 0")
    suspend fun getUnusedTiles(): List<DemTileEntity>

    @Query(
        """
        SELECT * FROM dem_tile
        WHERE ref_count = 0
        ORDER BY last_accessed_at ASC
        """
    )
    suspend fun getLruUnusedCandidates(): List<DemTileEntity>

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM dem_tile")
    suspend fun totalCacheSizeBytes(): Long

    @Query("DELETE FROM dem_tile WHERE tile_id = :tileId")
    suspend fun deleteById(tileId: String): Int

    @Query("UPDATE dem_tile SET last_accessed_at = :lastAccessedAt WHERE tile_id = :tileId")
    suspend fun touch(tileId: String, lastAccessedAt: Long): Int
}
