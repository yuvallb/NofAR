package com.nofar.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dem_tile",
    indices = [Index(value = ["ref_count", "last_accessed_at"])]
)
data class DemTileEntity(
    @PrimaryKey
    @ColumnInfo(name = "tile_id")
    val tileId: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "tile_lat") val tileLat: Int,
    @ColumnInfo(name = "tile_lon") val tileLon: Int,
    @ColumnInfo(name = "no_data_value") val noDataValue: Float,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "ref_count") val refCount: Int,
    @ColumnInfo(name = "last_accessed_at") val lastAccessedAt: Long
)
