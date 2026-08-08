@file:Suppress(
    "TooGenericExceptionCaught",
    "SwallowedException",
    "LongMethod",
    "CyclomaticComplexMethod",
    "MaxLineLength",
    "TooManyFunctions"
)

package com.nofar.core.data.prepare

import android.content.Context
import android.util.Log
import com.nofar.core.data.dem.DefaultGeoTiffConverter
import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.data.dem.GeoTiffConversionResult
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
import com.nofar.core.database.model.asEntity
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
    val regionId: UUID,
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

    fun toUserMessage(): String = when (this) {
        NoNetwork ->
            "No network connection. Connect to Wi-Fi or mobile data to download."
        AllMirrorsFailed ->
            "All OpenStreetMap mirrors failed. Try again later or check your connection."
        is PartialDemFailure ->
            "Download finished with $failedTiles missing elevation tile(s). You can retry to fill gaps."
        is Unknown -> message.ifBlank { "Download failed. You can retry to continue." }
    }

    companion object {
        fun fromThrowable(error: Throwable): PrepareDownloadError {
            if (error is PrepareDownloadException) return error.error
            val message = error.message.orEmpty()
            return when {
                message.contains("All Overpass mirrors", ignoreCase = true) -> AllMirrorsFailed
                message.contains("Unable to resolve host", ignoreCase = true) ||
                    message.contains("Failed to connect", ignoreCase = true) ||
                    message.contains("Network is unreachable", ignoreCase = true) ||
                    message.contains("No address associated", ignoreCase = true) ||
                    error is java.net.UnknownHostException ||
                    error is java.net.ConnectException -> NoNetwork
                message.contains("DEM", ignoreCase = true) &&
                    message.contains("fail", ignoreCase = true) ->
                    PartialDemFailure(failedTiles = 1)
                else -> Unknown(message.ifBlank { "Download failed. You can retry to continue." })
            }
        }
    }
}

class PrepareDownloadException(val error: PrepareDownloadError) : Exception(error.toUserMessage())

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

    private val _lastError = MutableStateFlow<PrepareDownloadError?>(null)
    val lastError: StateFlow<PrepareDownloadError?> = _lastError.asStateFlow()

    @Volatile
    private var cancelled = false

    @Volatile
    private var activeRegionId: UUID? = null

    @Volatile
    private var lastPersistedPercent = -1

    fun cancel() {
        cancelled = true
    }

    fun clearLastError() {
        _lastError.value = null
    }

    suspend fun download(regionId: UUID): Result<Unit> {
        cancelled = false
        activeRegionId = regionId
        lastPersistedPercent = -1
        _lastError.value = null
        val region =
            regionRepository.getRegion(regionId) ?: return Result.failure(IllegalStateException("Region missing"))

        regionRepository.updateDownloadStatus(regionId, DownloadStatus.DOWNLOADING, progressPct = 0)
        resetRegionCoverage(regionId)
        val collectionRadiusM = RegionBounds.dataCollectionRadiusM(region)
        val bbox =
            OverpassQueryBuilder.boundingBoxFromCircle(region.centerLat, region.centerLon, collectionRadiusM)
        val estimate = PrepareEstimator.estimate(region.centerLat, region.centerLon, collectionRadiusM)
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
                            regionId = regionId,
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
                val footprintByEntityId = mutableMapOf<String, Double>()
                var savedCount = 0
                entityCount =
                    overpassStreamParser.parse(
                        input = stream,
                        labelLanguage = labelLanguage,
                        onElement = { element ->
                            checkCancelled()
                            if (savedCount < MAX_OSM_ENTITIES) {
                                // Stream upserts — never buffer the full Overpass entity list.
                                coverageLinker.upsertAndLinkEntity(
                                    regionId = regionId.toString(),
                                    entity = overpassStreamParser.toGeoEntity(element).asEntity(),
                                    displayName = element.name
                                )
                                savedCount++
                                if (savedCount % 50 == 0) {
                                    updateProgress(
                                        PreparePhase.OSM,
                                        ((savedCount.toDouble() / MAX_OSM_ENTITIES) * 40)
                                            .toInt()
                                            .coerceIn(1, 39),
                                        message = "Saving OpenStreetMap features ($savedCount)…"
                                    )
                                }
                            }
                        },
                        onFootprint = { entityId, radiusM ->
                            val existing = footprintByEntityId[entityId]
                            footprintByEntityId[entityId] =
                                if (existing == null) {
                                    radiusM
                                } else {
                                    minOf(existing, radiusM)
                                }
                        }
                    )
                entityCount = savedCount
                applyFootprints(regionId, footprintByEntityId)
                updateProgress(
                    PreparePhase.OSM,
                    40,
                    message = "Saving OpenStreetMap features ($savedCount)…"
                )
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
            val tiles =
                DemTileId.intersectingTiles(
                    RegionBounds.boundingBox(region.centerLat, region.centerLon, collectionRadiusM)
                )
            if (tiles.size > MAX_DEM_TILES_PER_REGION) {
                throw IllegalStateException(
                    "Region requires ${tiles.size} DEM tiles (max $MAX_DEM_TILES_PER_REGION); " +
                        "move the center away from the poles or shrink the radius"
                )
            }
            val linkedTileIds = mutableListOf<String>()
            tiles.forEachIndexed { index, (tileLat, tileLon) ->
                checkCancelled()
                val tileId = DemTileId.fromCoordinates(tileLat, tileLon)
                val binFile = demTileRepository.demFile(tileId)
                if (demTileRepository.isBinReadable(tileId)) {
                    ensureTileRegistered(tileId, binFile)
                    acquireTileForRegion(regionId, tileId)
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
                if (binFile.exists()) {
                    binFile.delete()
                }

                val tifFile = File(demDirectory, "$tileId.tif")
                try {
                    if (!tifFile.exists()) {
                        demTileFetcher.fetchTile(
                            tileLat = tileLat,
                            tileLon = tileLon,
                            outputFile = tifFile,
                            isCancelled = { cancelled }
                        ) { bytesRead, totalBytes ->
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

                    upsertConvertedTile(
                        tileId = tileId,
                        tileLat = tileLat,
                        tileLon = tileLon,
                        conversion = conversion
                    )
                    acquireTileForRegion(regionId, tileId)
                    linkedTileIds.add(tileId)
                    val completedPct = 40 + ((index + 1) * 50 / tiles.size.coerceAtLeast(1))
                    persistProgress(completedPct)
                } catch (error: Exception) {
                    demFailures++
                    Log.w(TAG, "DEM tile failed for $tileId", error)
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
            val elevationFillOk =
                postProcessor.process(regionId) { processed, total ->
                    val pct = 90 + ((processed * 9) / total.coerceAtLeast(1))
                    updateProgress(
                        PreparePhase.POST_PROCESSING,
                        pct.coerceIn(90, 99),
                        message = "Filling elevations ($processed/$total)…"
                    )
                }
            // Drop entities left without coverage after re-download (Requirements §5.3).
            geoEntityRepository.garbageCollectOrphans()
            updateProgress(PreparePhase.POST_PROCESSING, 100, message = "Finalizing…")

            val terminalStatus =
                when {
                    demFailures > 0 -> DownloadStatus.PARTIAL
                    entityCount == 0 -> DownloadStatus.PARTIAL
                    !elevationFillOk -> DownloadStatus.PARTIAL
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
            Log.e(TAG, "Prepare download failed for region $regionId", error)
            _lastError.value = PrepareDownloadError.fromThrowable(error)
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

    private suspend fun applyFootprints(regionId: UUID, footprintByEntityId: Map<String, Double>) {
        if (footprintByEntityId.isEmpty()) return
        for ((entityId, radiusM) in footprintByEntityId) {
            checkCancelled()
            val entity = geoEntityRepository.getById(entityId) ?: continue
            coverageLinker.upsertAndLinkEntity(
                regionId = regionId.toString(),
                entity = entity.copy(footprintRadiusM = radiusM).asEntity(),
                displayName = entity.name
            )
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

    private suspend fun upsertConvertedTile(
        tileId: String,
        tileLat: Int,
        tileLon: Int,
        conversion: GeoTiffConversionResult
    ) {
        val existing = demTileRepository.getTile(tileId)
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
                // Preserve refs held by other regions; this region's claim is added via acquire.
                refCount = existing?.refCount ?: 0,
                lastAccessedAt = Instant.now()
            )
        )
    }

    /**
     * Links [tileId] to [regionId] and increments refCount only when the coverage row is new.
     * Avoids inflating refs on retries / forced re-converts when the junction already exists.
     */
    private suspend fun acquireTileForRegion(regionId: UUID, tileId: String) {
        if (linkTileCoverage(regionId, tileId)) {
            demTileRepository.incrementRefCount(tileId)
        }
    }

    private suspend fun linkTileCoverage(regionId: UUID, tileId: String): Boolean {
        val rowId =
            tileCoverageDao.insert(
                TileCoverageEntity(
                    regionId = regionId.toString(),
                    tileId = tileId
                )
            )
        return rowId != -1L
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
        val regionId = activeRegionId ?: return
        _progress.value =
            PrepareProgress(
                regionId = regionId,
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

    companion object {
        private const val TAG = "PrepareDownload"

        /** Hard cap so a pathological Overpass response cannot grow the Room DB unboundedly. */
        const val MAX_OSM_ENTITIES: Int = 50_000

        /** Caps polar / corrupted-region tile explosions (20 km + padding ≈ a few tiles mid-lat). */
        const val MAX_DEM_TILES_PER_REGION: Int = 64
    }
}
