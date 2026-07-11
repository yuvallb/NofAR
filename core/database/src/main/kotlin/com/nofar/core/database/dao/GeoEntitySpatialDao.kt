package com.nofar.core.database.dao

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * Spatial and R-tree maintenance queries. Kept separate from [GeoEntityDao] because they
 * join the virtual [geo_entity_rtree] table (created at runtime via [com.nofar.core.database.RTreeCallback])
 * and must run through Room — not [androidx.room.RoomDatabase.openHelper] — when using
 * [androidx.sqlite.driver.bundled.BundledSQLiteDriver].
 */
@Dao
interface GeoEntitySpatialDao {
    @RawQuery
    suspend fun queryLongs(query: SupportSQLiteQuery): List<Long>

    @RawQuery
    suspend fun executeStatement(query: SupportSQLiteQuery): Int
}
