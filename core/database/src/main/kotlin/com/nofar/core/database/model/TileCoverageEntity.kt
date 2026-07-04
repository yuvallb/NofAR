package com.nofar.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "tile_coverage",
    primaryKeys = ["region_id", "tile_id"],
    foreignKeys = [
        ForeignKey(
            entity = RegionEntity::class,
            parentColumns = ["id"],
            childColumns = ["region_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DemTileEntity::class,
            parentColumns = ["tile_id"],
            childColumns = ["tile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tile_id"])]
)
data class TileCoverageEntity(
    @ColumnInfo(name = "region_id") val regionId: String,
    @ColumnInfo(name = "tile_id") val tileId: String
)
