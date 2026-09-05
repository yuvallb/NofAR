@file:Suppress("TooManyFunctions")

package com.nofar.core.database.model

import com.nofar.core.model.CoverageCell
import com.nofar.core.model.CoverageEntityCoverage
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DemTile
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.ElevationSource
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.LabelLanguage
import com.nofar.core.model.ModelJson
import com.nofar.core.model.OsmType
import java.time.Instant
import java.util.UUID

fun CoverageSetEntity.asExternalModel(): CoverageSet = CoverageSet(
    id = UUID.fromString(id),
    name = name,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    downloadStatus = DownloadStatus.valueOf(downloadStatus),
    downloadProgressPct = downloadProgressPct,
    osmDatasetVersion = osmDatasetVersion?.let(Instant::ofEpochMilli),
    estimatedSizeBytes = estimatedSizeBytes,
    entityCount = entityCount,
    labelLanguage = LabelLanguage.fromStoredName(labelLanguage)
)

fun CoverageSet.asEntity(): CoverageSetEntity = CoverageSetEntity(
    id = id.toString(),
    name = name,
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

fun CoverageEntityEntity.asExternalModel(): CoverageEntityCoverage = CoverageEntityCoverage(
    coverageSetId = UUID.fromString(coverageSetId),
    entityId = entityId,
    displayName = displayName
)

fun CoverageEntityCoverage.asEntity(): CoverageEntityEntity = CoverageEntityEntity(
    coverageSetId = coverageSetId.toString(),
    entityId = entityId,
    displayName = displayName
)

fun CoverageCellEntity.asExternalModel(): CoverageCell = CoverageCell(
    coverageSetId = UUID.fromString(coverageSetId),
    cellId = cellId
)

fun CoverageCell.asEntity(): CoverageCellEntity = CoverageCellEntity(
    coverageSetId = coverageSetId.toString(),
    cellId = cellId
)

fun CoverageSetEntity.toDebugJson(): String = asExternalModel().let(ModelJson::coverageSetToJson)

fun GeoEntityEntity.toDebugJson(): String = asExternalModel().let(ModelJson::geoEntityToJson)

fun DemTileEntity.toDebugJson(): String = asExternalModel().let(ModelJson::demTileToJson)
