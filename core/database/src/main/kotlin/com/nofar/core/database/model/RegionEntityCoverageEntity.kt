package com.nofar.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "region_entity_coverage",
    primaryKeys = ["region_id", "entity_id"],
    foreignKeys = [
        ForeignKey(
            entity = RegionEntity::class,
            parentColumns = ["id"],
            childColumns = ["region_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GeoEntityEntity::class,
            parentColumns = ["id"],
            childColumns = ["entity_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["entity_id"])]
)
data class RegionEntityCoverageEntity(
    @ColumnInfo(name = "region_id") val regionId: String,
    @ColumnInfo(name = "entity_id") val entityId: String
)
