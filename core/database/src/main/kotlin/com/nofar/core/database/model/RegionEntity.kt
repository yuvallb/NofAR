package com.nofar.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "region",
    indices = [Index(value = ["updated_at"])]
)
data class RegionEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "center_lat") val centerLat: Double,
    @ColumnInfo(name = "center_lon") val centerLon: Double,
    @ColumnInfo(name = "radius_m") val radiusM: Double,
    @ColumnInfo(name = "min_lat") val minLat: Double,
    @ColumnInfo(name = "max_lat") val maxLat: Double,
    @ColumnInfo(name = "min_lon") val minLon: Double,
    @ColumnInfo(name = "max_lon") val maxLon: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "download_status") val downloadStatus: String,
    @ColumnInfo(name = "download_progress_pct") val downloadProgressPct: Int,
    @ColumnInfo(name = "osm_dataset_version") val osmDatasetVersion: Long?,
    @ColumnInfo(name = "estimated_size_bytes") val estimatedSizeBytes: Long,
    @ColumnInfo(name = "entity_count") val entityCount: Int
)
