@file:Suppress("TooManyFunctions")

package com.nofar.core.database.model

import com.nofar.core.model.DemTile
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.ElevationSource
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.LabelLanguage
import com.nofar.core.model.ModelJson
import com.nofar.core.model.OsmType
import com.nofar.core.model.Region
import com.nofar.core.model.RegionEntityCoverage
import com.nofar.core.model.TileCoverage
import java.time.Instant
import java.util.UUID

fun RegionEntity.asExternalModel(): Region = Region(
    id = UUID.fromString(id),
    name = name,
    centerLat = centerLat,
    centerLon = centerLon,
    radiusM = radiusM,
    minLat = minLat,
    maxLat = maxLat,
    minLon = minLon,
    maxLon = maxLon,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    downloadStatus = DownloadStatus.valueOf(downloadStatus),
    downloadProgressPct = downloadProgressPct,
    osmDatasetVersion = osmDatasetVersion?.let(Instant::ofEpochMilli),
    estimatedSizeBytes = estimatedSizeBytes,
    entityCount = entityCount,
    labelLanguage = LabelLanguage.fromStoredName(labelLanguage)
)

fun Region.asEntity(): RegionEntity = RegionEntity(
    id = id.toString(),
    name = name,
    centerLat = centerLat,
    centerLon = centerLon,
    radiusM = radiusM,
    minLat = minLat,
    maxLat = maxLat,
    minLon = minLon,
    maxLon = maxLon,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    downloadStatus = downloadStatus.name,
    downloadProgressPct = downloadProgressPct,
    osmDatasetVersion = osmDatasetVersion?.toEpochMilli(),
    estimatedSizeBytes = estimatedSizeBytes,
    entityCount = entityCount,
    labelLanguage = labelLanguage.name
)

fun GeoEntityEntity.asExternalModel(): GeoEntity = GeoEntity(
    id = id,
    osmType = OsmType.valueOf(osmType),
    name = name,
    type = GeoEntityType.fromStoredName(type) ?: GeoEntityType.LOCALITY,
    lat = lat,
    lon = lon,
    elevation = elevation,
    elevationSource = elevationSource?.let(ElevationSource::valueOf),
    lastSeenAt = Instant.ofEpochMilli(lastSeenAt),
    footprintRadiusM = footprintRadiusM
)

fun GeoEntity.asEntity(): GeoEntityEntity = GeoEntityEntity(
    id = id,
    osmType = osmType.name,
    name = name,
    type = type.name,
    lat = lat,
    lon = lon,
    elevation = elevation,
    elevationSource = elevationSource?.name,
    lastSeenAt = lastSeenAt.toEpochMilli(),
    footprintRadiusM = footprintRadiusM
)

fun DemTileEntity.asExternalModel(): DemTile = DemTile(
    tileId = tileId,
    filePath = filePath,
    width = width,
    height = height,
    tileLat = tileLat,
    tileLon = tileLon,
    noDataValue = noDataValue,
    sizeBytes = sizeBytes,
    refCount = refCount,
    lastAccessedAt = Instant.ofEpochMilli(lastAccessedAt)
)

fun DemTile.asEntity(): DemTileEntity = DemTileEntity(
    tileId = tileId,
    filePath = filePath,
    width = width,
    height = height,
    tileLat = tileLat,
    tileLon = tileLon,
    noDataValue = noDataValue,
    sizeBytes = sizeBytes,
    refCount = refCount,
    lastAccessedAt = lastAccessedAt.toEpochMilli()
)

fun RegionEntityCoverageEntity.asExternalModel(): RegionEntityCoverage = RegionEntityCoverage(
    regionId = UUID.fromString(regionId),
    entityId = entityId,
    displayName = displayName
)

fun RegionEntityCoverage.asEntity(): RegionEntityCoverageEntity = RegionEntityCoverageEntity(
    regionId = regionId.toString(),
    entityId = entityId,
    displayName = displayName
)

fun TileCoverageEntity.asExternalModel(): TileCoverage = TileCoverage(
    regionId = UUID.fromString(regionId),
    tileId = tileId
)

fun TileCoverage.asEntity(): TileCoverageEntity = TileCoverageEntity(
    regionId = regionId.toString(),
    tileId = tileId
)

fun RegionEntity.toDebugJson(): String = asExternalModel().let(ModelJson::regionToJson)

fun GeoEntityEntity.toDebugJson(): String = asExternalModel().let(ModelJson::geoEntityToJson)

fun DemTileEntity.toDebugJson(): String = asExternalModel().let(ModelJson::demTileToJson)
