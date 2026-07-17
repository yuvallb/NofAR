@file:Suppress(
    "TooGenericExceptionCaught",
    "SwallowedException",
    "LongMethod",
    "CyclomaticComplexMethod",
    "MaxLineLength"
)

package com.nofar.core.data.prepare

import android.content.Context
import com.nofar.core.data.dem.DefaultGeoTiffConverter
import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.data.dem.GeoTiffConverter
import com.nofar.core.data.osm.OverpassStreamParser
import com.nofar.core.data.preferences.UserPreferencesRepository
import com.nofar.core.data.repository.DefaultDemTileRepository
import com.nofar.core.data.repository.GeoEntityRepository
import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.data.usecase.LruEvictionUseCase
import com.nofar.core.database.dao.CoverageLinker
import com.nofar.core.database.dao.RegionEntityCoverageDao
import com.nofar.core.database.dao.TileCoverageDao
import com.nofar.core.database.model.TileCoverageEntity
import com.nofar.core.model.DemTile
import com.nofar.core.model.DemTileId
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.RegionBounds
import com.nofar.core.network.DemTileFetcher
import com.nofar.core.network.OverpassApi
import com.nofar.core.network.OverpassQueryBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class PrepareProgress(
    val phase: PreparePhase,
    val overallPercent: Int,
    val osmBytesRead: Long = 0L,
    val demTileIndex: Int = 0,
    val demTileCount: Int = 0,
    val remainingBytesEstimate: Long = 0L,
    val message: String = ""
)

enum class PreparePhase {
    OSM,
    DEM,
    POST_PROCESSING
}

sealed interface PrepareDownloadError {
    data object NoNetwork : PrepareDownloadError

    data object AllMirrorsFailed : PrepareDownloadError

    data class PartialDemFailure(val failedTiles: Int) : PrepareDownloadError

    data class Unknown(val message: String) : PrepareDownloadError
}

@Singleton
class PrepareDownloadOrchestrator
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
    private val geoEntityRepository: GeoEntityRepository,
    private val demTileRepository: DefaultDemTileRepository,
    private val overpassApi: OverpassApi,
    private val demTileFetcher: DemTileFetcher,
    private val regionEntityCoverageDao: RegionEntityCoverageDao,
    private val tileCoverageDao: TileCoverageDao,
    private val coverageLinker: CoverageLinker,
    private val postProcessor: PreparePostProcessor,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val lruEvictionUseCase: LruEvictionUseCase,
    private val geoTiffConverter: GeoTiffConverter = DefaultGeoTiffConverter(),
    private val overpassStreamParser: OverpassStreamParser = OverpassStreamParser()
) {
    private val _progress = MutableStateFlow<PrepareProgress?>(null)
    val progress: StateFlow<PrepareProgress?> = _progress.asStateFlow()

    @Volatile
    private var cancelled = false

    @Volatile
    private var activeRegionId: UUID? = null

    @Volatile
    private var lastPersistedPercent = -1

    fun cancel() {
        cancelled = true
    }

    suspend fun download(regionId: UUID): Result<Unit> {
        cancelled = false
        activeRegionId = regionId
        lastPersistedPercent = -1
        val region =
            regionRepository.getRegion(regionId) ?: return Result.failure(IllegalStateException("Region missing"))

        regionRepository.updateDownloadStatus(regionId, DownloadStatus.DOWNLOADING, progressPct = 0)
        resetRegionCoverage(regionId)
        val bbox = OverpassQueryBuilder.boundingBoxFromCircle(region.centerLat, region.centerLon, region.radiusM)
        val estimate = PrepareEstimator.estimate(region.centerLat, region.centerLon, region.radiusM)
        var osmDatasetVersion = Instant.now()
        var entityCount = 0
        var demFailures = 0

        return try {
            // OSM phase (0–40%)
            updateProgress(PreparePhase.OSM, 0, message = "Contacting OpenStreetMap servers…")
            persistProgress(0)
            val overpassResponse =
                overpassApi.queryRegion(bbox) { bytes ->
                    val pct =
                        (
                            (
                                bytes.toDouble() / estimate.osmEstimateBytes.coerceAtLeast(
                                    1
                                )
                                ) * 40
                            ).toInt().coerceIn(0, 40)
                    _progress.value =
                        PrepareProgress(
                            phase = PreparePhase.OSM,
                            overallPercent = pct,
                            osmBytesRead = bytes,
                            message = "Downloading OSM data…"
                        )
                }
            osmDatasetVersion = overpassResponse.datasetVersion
            // Prefer the live Settings/Prepare preference at download time so a re-download
            // never keeps a stale region.labelLanguage from an earlier parse.
            val labelLanguage = userPreferencesRepository.preferredLabelLanguage.first()
            if (region.labelLanguage != labelLanguage) {
                regionRepository.updateRegion(region.copy(labelLanguage = labelLanguage))
            }
            overpassResponse.body.use { stream ->
                val parsedEntities = mutableListOf<com.nofar.core.data.osm.ParsedOsmElement>()
                val linkedEntities = mutableListOf<Pair<String, String>>()
                entityCount =
                    overpassStreamParser.parse(stream, labelLanguage) { element ->
                        checkCancelled()
                        parsedEntities.add(element)
                    }
                updateProgress(
                    PreparePhase.OSM,
                    _progress.value?.overallPercent?.coerceAtLeast(1) ?: 1,
                    message = "Saving OpenStreetMap features…"
                )
                parsedEntities.forEachIndexed { index, element ->
                    checkCancelled()
                    val geoEntity = overpassStreamParser.toGeoEntity(element)
                    geoEntityRepository.upsert(geoEntity)
                    linkedEntities.add(geoEntity.id to element.name)
                    if ((index + 1) % 50 == 0 || index + 1 == entityCount) {
                        val ingestPct =
                            if (entityCount > 0) {
                                (((index + 1).toDouble() / entityCount) * 40).toInt().coerceIn(1, 40)
                            } else {
                                40
                            }
                        updateProgress(
                            PreparePhase.OSM,
                            ingestPct,
                            message = "Saving OpenStreetMap features (${index + 1}/$entityCount)…"
                        )
                    }
                }
                if (linkedEntities.isNotEmpty()) {
                    coverageLinker.linkEntities(regionId.toString(), linkedEntities)
                }
            }
            val coverageCount = regionEntityCoverageDao.getEntityIdsForRegion(regionId.toString()).size
            if (coverageCount != entityCount) {
                entityCount = maxOf(entityCount, coverageCount)
            }
            regionRepository.updateDownloadStatus(
                regionId,
                DownloadStatus.DOWNLOADING,
                progressPct = 40,
                osmDatasetVersion = osmDatasetVersion,
                entityCount = entityCount
            )
            applyOsmAutoName(regionId)

            // DEM phase (40–90%)
            val tiles = DemTileId.intersectingTiles(
                RegionBounds.boundingBox(region.centerLat, region.centerLon, region.radiusM)
            )
            val linkedTileIds = mutableListOf<String>()
            tiles.forEachIndexed { index, (tileLat, tileLon) ->
                checkCancelled()
                val tileId = DemTileId.fromCoordinates(tileLat, tileLon)
                val binFile = demTileRepository.demFile(tileId)
                if (binFile.exists()) {
                    ensureTileRegistered(tileId, binFile)
                    demTileRepository.incrementRefCount(tileId)
                    linkTileCoverage(regionId, tileId)
                    linkedTileIds.add(tileId)
                    val pct = 40 + ((index + 1) * 50 / tiles.size.coerceAtLeast(1))
                    updateProgress(
                        PreparePhase.DEM,
                        pct,
                        demTileIndex = index + 1,
                        demTileCount = tiles.size
                    )
                    persistProgress(pct)
                    return@forEachIndexed
                }

                val tifFile = File(demDirectory, "$tileId.tif")
                try {
                    demTileFetcher.fetchTile(tileLat, tileLon, tifFile) { bytesRead, totalBytes ->
                        val tileFraction = if (totalBytes != null && totalBytes > 0) {
                            bytesRead.toDouble() / totalBytes
                        } else {
                            0.5
                        }
                        val pct =
                            40 + (((index + tileFraction * 0.7) * 50) / tiles.size.coerceAtLeast(1)).toInt()
                        val remaining =
                            estimate.totalEstimateBytes - (estimate.osmEstimateBytes + bytesRead)
                        updateProgress(
                            PreparePhase.DEM,
                            pct.coerceIn(40, 90),
                            demTileIndex = index + 1,
                            demTileCount = tiles.size,
                            remainingBytesEstimate = remaining.coerceAtLeast(0),
                            message = "Downloading DEM tile ${index + 1}/${tiles.size}"
                        )
                    }

                    val convertPct =
                        40 + (((index + 0.85) * 50) / tiles.size.coerceAtLeast(1)).toInt()
                    updateProgress(
                        PreparePhase.DEM,
                        convertPct.coerceIn(40, 90),
                        demTileIndex = index + 1,
                        demTileCount = tiles.size,
                        message = "Converting tile ${index + 1}/${tiles.size} to local binary…"
                    )
                    val conversion = geoTiffConverter.convert(tifFile, tileLat, tileLon, binFile)
                    val keepRawTif = userPreferencesRepository.keepRawGeoTiff.first()
                    if (!keepRawTif) {
                        tifFile.delete()
                    }

                    val existing = demTileRepository.getTile(tileId)
                    if (existing == null) {
                        demTileRepository.registerTile(
                            DemTile(
                                tileId = tileId,
                                filePath = demTileRepository.demFilePath(tileId),
                                width = conversion.width,
                                height = conversion.height,
                                tileLat = tileLat,
                                tileLon = tileLon,
                                noDataValue = conversion.noDataValue,
                                sizeBytes = conversion.sizeBytes,
                                refCount = 0,
                                lastAccessedAt = Instant.now()
                            )
                        )
                    }
                    demTileRepository.incrementRefCount(tileId)
                    linkTileCoverage(regionId, tileId)
                    linkedTileIds.add(tileId)
                    val completedPct = 40 + ((index + 1) * 50 / tiles.size.coerceAtLeast(1))
                    persistProgress(completedPct)
                } catch (error: Exception) {
                    demFailures++
                    tifFile.delete()
                }
            }
            if (linkedTileIds.isNotEmpty()) {
                val existingTileIds = tileCoverageDao.getTileIdsForRegion(regionId.toString())
                if (existingTileIds.isEmpty()) {
                    coverageLinker.linkTiles(regionId.toString(), linkedTileIds)
                }
            }

            // Post-processing (90–100%)
            updateProgress(PreparePhase.POST_PROCESSING, 90, message = "Filling elevations…")
            persistProgress(90)
            postProcessor.process(regionId) { processed, total ->
                val pct = 90 + ((processed * 9) / total.coerceAtLeast(1))
                updateProgress(
                    PreparePhase.POST_PROCESSING,
                    pct.coerceIn(90, 99),
                    message = "Filling elevations ($processed/$total)…"
                )
            }
            updateProgress(PreparePhase.POST_PROCESSING, 100, message = "Finalizing…")

            val terminalStatus =
                when {
                    demFailures > 0 -> DownloadStatus.PARTIAL
                    entityCount == 0 -> DownloadStatus.PARTIAL
                    else -> DownloadStatus.READY
                }
            regionRepository.updateDownloadStatus(regionId, terminalStatus, progressPct = 100)

            try {
                enforceDemCacheLimit()
            } catch (_: Exception) {
                // Region is already READY/PARTIAL; cache eviction is best-effort.
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val status =
                if (entityCount > 0 || demFailures > 0) DownloadStatus.PARTIAL else DownloadStatus.NOT_DOWNLOADED
            regionRepository.updateDownloadStatus(regionId, status, progressPct = _progress.value?.overallPercent ?: 0)
            Result.failure(error)
        } finally {
            if (activeRegionId == regionId) {
                _progress.value = null
                activeRegionId = null
            }
        }
    }

    private suspend fun applyOsmAutoName(regionId: UUID) {
        val region = regionRepository.getRegion(regionId)
        if (region != null && !RegionNamePolicy.isUserProvidedName(region.name)) {
            val entityIds = regionEntityCoverageDao.getEntityIdsForRegion(regionId.toString())
            val entities = entityIds.mapNotNull { entityId -> geoEntityRepository.getById(entityId) }
            RegionNameResolver.closestEntityName(region, entities)?.let { chosenName ->
                regionRepository.updateRegionName(regionId, chosenName)
            }
        }
    }

    private suspend fun resetRegionCoverage(regionId: UUID) {
        val regionIdString = regionId.toString()
        val oldTileIds = tileCoverageDao.getTileIdsForRegion(regionIdString)
        tileCoverageDao.deleteForRegion(regionIdString)
        regionEntityCoverageDao.deleteForRegion(regionIdString)
        oldTileIds.forEach { tileId ->
            demTileRepository.decrementRefCount(tileId)
            if (demTileRepository.getTile(tileId)?.refCount == 0) {
                demTileRepository.evictTile(tileId)
            }
        }
    }

    private suspend fun ensureTileRegistered(tileId: String, binFile: File) {
        if (demTileRepository.getTile(tileId) != null) return
        DemTileReader.open(binFile).use { reader ->
            demTileRepository.registerTile(
                DemTile(
                    tileId = tileId,
                    filePath = demTileRepository.demFilePath(tileId),
                    width = reader.width,
                    height = reader.height,
                    tileLat = reader.tileLat,
                    tileLon = reader.tileLon,
                    noDataValue = reader.noDataValue,
                    sizeBytes = binFile.length(),
                    refCount = 0,
                    lastAccessedAt = Instant.now()
                )
            )
        }
    }

    private suspend fun linkTileCoverage(regionId: UUID, tileId: String) {
        tileCoverageDao.insert(
            TileCoverageEntity(
                regionId = regionId.toString(),
                tileId = tileId
            )
        )
    }

    private fun updateProgress(
        phase: PreparePhase,
        overallPercent: Int,
        osmBytesRead: Long = _progress.value?.osmBytesRead ?: 0L,
        demTileIndex: Int = _progress.value?.demTileIndex ?: 0,
        demTileCount: Int = _progress.value?.demTileCount ?: 0,
        remainingBytesEstimate: Long = _progress.value?.remainingBytesEstimate ?: 0L,
        message: String = _progress.value?.message ?: ""
    ) {
        _progress.value =
            PrepareProgress(
                phase = phase,
                overallPercent = overallPercent.coerceIn(0, 100),
                osmBytesRead = osmBytesRead,
                demTileIndex = demTileIndex,
                demTileCount = demTileCount,
                remainingBytesEstimate = remainingBytesEstimate,
                message = message
            )
    }

    private suspend fun persistProgress(overallPercent: Int) {
        val regionId = activeRegionId ?: return
        val pct = overallPercent.coerceIn(0, 100)
        if (pct >= lastPersistedPercent + 5 || pct == 0 || pct == 100) {
            lastPersistedPercent = pct
            regionRepository.updateDownloadStatus(regionId, DownloadStatus.DOWNLOADING, pct)
        }
    }

    private fun checkCancelled() {
        if (cancelled) throw CancellationException("Prepare download cancelled")
    }

    private suspend fun enforceDemCacheLimit() {
        val limitBytes = userPreferencesRepository.demCacheLimitBytes.first()
        lruEvictionUseCase.execute(limitBytes)
    }

    private val demDirectory: File
        get() = File(context.filesDir, "dem/raw").also { it.mkdirs() }
}
