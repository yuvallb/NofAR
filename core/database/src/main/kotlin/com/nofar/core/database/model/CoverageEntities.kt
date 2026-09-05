package com.nofar.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "coverage_set",
    indices = [Index(value = ["updated_at"])]
)
data class CoverageSetEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "download_status") val downloadStatus: String,
    @ColumnInfo(name = "download_progress_pct") val downloadProgressPct: Int,
    @ColumnInfo(name = "osm_dataset_version") val osmDatasetVersion: Long?,
    @ColumnInfo(name = "estimated_size_bytes") val estimatedSizeBytes: Long,
    @ColumnInfo(name = "entity_count") val entityCount: Int,
    @ColumnInfo(name = "label_language") val labelLanguage: String = "DEFAULT"
)

@Entity(
    tableName = "coverage_cell",
    primaryKeys = ["coverage_set_id", "cell_id"],
    foreignKeys = [
        ForeignKey(
            entity = CoverageSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["coverage_set_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["cell_id"])]
)
data class CoverageCellEntity(
    @ColumnInfo(name = "coverage_set_id") val coverageSetId: String,
    @ColumnInfo(name = "cell_id") val cellId: String
)

@Entity(
    tableName = "coverage_entity",
    primaryKeys = ["coverage_set_id", "entity_id"],
    foreignKeys = [
        ForeignKey(
            entity = CoverageSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["coverage_set_id"],
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
data class CoverageEntityEntity(
    @ColumnInfo(name = "coverage_set_id") val coverageSetId: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "display_name") val displayName: String
)
