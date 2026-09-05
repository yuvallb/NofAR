package com.nofar.core.model

import java.time.Instant
import java.util.UUID

/** A downloaded offline map coverage set (country pack or local 3×3 cell ring). */
data class CoverageSet(
    val id: UUID,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val downloadStatus: DownloadStatus,
    val downloadProgressPct: Int,
    val osmDatasetVersion: Instant?,
    val estimatedSizeBytes: Long,
    val entityCount: Int,
    val labelLanguage: LabelLanguage = LabelLanguage.DEFAULT
)

data class GeoEntity(
    val id: String,
    val osmType: OsmType,
    val name: String,
    val type: GeoEntityType,
    val lat: Double,
    val lon: Double,
    /** Orthometric height in whole meters (OSM `ele` or DEM sample). */
    val elevation: Int?,
    val elevationSource: ElevationSource?,
    val lastSeenAt: Instant,
    /** Approximate ground footprint radius (meters), from type defaults at Prepare time. */
    val footprintRadiusM: Double? = null
)

data class DemTile(
    val tileId: String,
    val filePath: String,
    val width: Int,
    val height: Int,
    val tileLat: Int,
    val tileLon: Int,
    val noDataValue: Float,
    val sizeBytes: Long,
    val refCount: Int,
    val lastAccessedAt: Instant
)

data class CoverageCell(val coverageSetId: UUID, val cellId: String)

data class CoverageEntityCoverage(val coverageSetId: UUID, val entityId: String, val displayName: String)
