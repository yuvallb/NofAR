package com.nofar.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "geo_entity",
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["type"])
    ]
)
data class GeoEntityEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "row_id")
    val rowId: Long = 0,
    val id: String,
    @ColumnInfo(name = "osm_type") val osmType: String,
    val name: String,
    val type: String,
    val lat: Double,
    val lon: Double,
    val elevation: Double?,
    @ColumnInfo(name = "elevation_source") val elevationSource: String?,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
    @ColumnInfo(name = "footprint_radius_m") val footprintRadiusM: Double? = null
)
