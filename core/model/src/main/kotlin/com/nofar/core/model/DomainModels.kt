package com.nofar.core.model

import java.time.Instant
import java.util.UUID

data class Region(
    val id: UUID,
    val name: String,
    val centerLat: Double,
    val centerLon: Double,
    val radiusM: Double,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
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
    val elevation: Double?,
    val elevationSource: ElevationSource?,
    val lastSeenAt: Instant,
    /** Approximate ground footprint radius (meters), derived from OSM boundary geometry at Prepare time. */
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

data class RegionEntityCoverage(val regionId: UUID, val entityId: String, val displayName: String)

data class TileCoverage(val regionId: UUID, val tileId: String)
