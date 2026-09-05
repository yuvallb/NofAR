@file:Suppress(
    "TooGenericExceptionCaught",
    "SwallowedException",
    "LongMethod",
    "CyclomaticComplexMethod",
    "MaxLineLength",
    "TooManyFunctions",
    "ReturnCount"
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
import com.nofar.core.data.repository.CoverageSetRepository
import com.nofar.core.data.repository.DefaultDemTileRepository
import com.nofar.core.data.repository.GeoEntityRepository
import com.nofar.core.data.usecase.LruEvictionUseCase
import com.nofar.core.database.dao.CoverageCellDao
import com.nofar.core.database.dao.CoverageEntityDao
import com.nofar.core.database.dao.CoverageLinker
import com.nofar.core.database.model.asEntity
import com.nofar.core.model.DemTile
import com.nofar.core.model.DemTileId
import com.nofar.core.model.DownloadStatus
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class PrepareProgress(
    val coverageSetId: UUID,
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
    private val coverageSetRepository: CoverageSetRepository,
    private val geoEntityRepository: GeoEntityRepository,
    private val demTileRepository: DefaultDemTileRepository,
    private val overpassApi: OverpassApi,
    private val demTileFetcher: DemTileFetcher,
    private val coverageEntityDao: CoverageEntityDao,
    private val coverageCellDao: CoverageCellDao,
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
    private var activeCoverageSetId: UUID? = null

    @Volatile
    private var lastPersistedPercent = -1

    fun cancel() {
        cancelled = true
    }

    fun clearLastError() {
        _lastError.value = null
    }

    suspend fun download(coverageSetId: UUID): Result<Unit> {
        cancelled = false
        activeCoverageSetId = coverageSetId
        lastPersistedPercent = -1
        _lastError.value = null
        val coverageSet =
            coverageSetRepository.getCoverageSet(coverageSetId)
                ?: return Result.failure(IllegalStateException("Coverage set missing"))

        val cells =
            coverageCellDao.getCellIdsForCoverageSet(coverageSetId.toString())
                .mapNotNull { DemTileId.parse(it) }
        if (cells.isEmpty()) {
            coverageSetRepository.updateDownloadStatus(
                coverageSetId,
                DownloadStatus.NOT_DOWNLOADED,
                progressPct = 0
            )
            return Result.failure(IllegalStateException("No cells configured for coverage set"))
        }
        val estimate = PrepareEstimator.estimateForCells(cells)
        val cacheLimit = userPreferencesRepository.demCacheLimitBytes.first()
        val budget = (cacheLimit * com.nofar.core.model.AppConfig.COVERAGE_BYTE_BUDGET_CACHE_FRACTION).toLong()
        if (estimate.demEstimateMinBytes > budget) {
            coverageSetRepository.updateDownloadStatus(
                coverageSetId,
                DownloadStatus.NOT_DOWNLOADED,
                progressPct = 0
            )
            return Result.failure(
                IllegalStateException(
                    "Coverage set DEM (${estimate.demEstimateMinBytes} bytes) exceeds " +
                        "${(com.nofar.core.model.AppConfig.COVERAGE_BYTE_BUDGET_CACHE_FRACTION * 100).toInt()}% of cache limit"
                )
            )
        }

        coverageSetRepository.updateDownloadStatus(coverageSetId, DownloadStatus.DOWNLOADING, progressPct = 0)
        // Clear OSM links only — keep DEM files and ref counts so retries resume.
        coverageEntityDao.deleteForCoverageSet(coverageSetId.toString())
        var osmDatasetVersion = Instant.now()
        var entityCount = 0
        var demFailures = 0

        return try {
            updateProgress(PreparePhase.OSM, 0, message = "Contacting OpenStreetMap servers…")
            persistProgress(0)
            val labelLanguage = userPreferencesRepository.preferredLabelLanguage.first()
            if (coverageSet.labelLanguage != labelLanguage) {
                coverageSetRepository.updateCoverageSet(coverageSet.copy(labelLanguage = labelLanguage))
            }

            var totalOsmBytes = 0L
            cells.forEachIndexed { cellIndex, (tileLat, tileLon) ->
                checkCancelled()
                if (cellIndex > 0) {
                    delay(OVERPASS_CELL_GAP_MS)
                }
                val bbox = OverpassQueryBuilder.boundingBoxForCell(tileLat, tileLon)
                val overpassResponse =
                    overpassApi.queryRegion(bbox) { bytes ->
                        totalOsmBytes += bytes
                        val cellFraction =
                            (cellIndex + bytes.toDouble() / estimate.osmEstimateBytes.coerceAtLeast(1)) / cells.size
                        val pct = (cellFraction * 40).toInt().coerceIn(0, 40)
                        _progress.value =
                            PrepareProgress(
                                coverageSetId = coverageSetId,
                                phase = PreparePhase.OSM,
                                overallPercent = pct,
                                osmBytesRead = totalOsmBytes,
                                message = "Downloading OSM cell ${cellIndex + 1}/${cells.size}…"
                            )
                    }
                osmDatasetVersion = overpassResponse.datasetVersion
                var savedInCell = 0
                overpassResponse.body.use { stream ->
                    overpassStreamParser.parse(
                        input = stream,
                        labelLanguage = labelLanguage,
                        onElement = { element ->
                            checkCancelled()
                            if (savedInCell < MAX_OSM_ENTITIES_PER_CELL) {
                                coverageLinker.upsertAndLinkEntity(
                                    coverageSetId = coverageSetId.toString(),
                                    entity = overpassStreamParser.toGeoEntity(element).asEntity(),
                                    displayName = element.name
                                )
                                savedInCell++
                            }
                        },
                        onFootprint = { _, _ -> }
                    )
                }
            }
            entityCount = coverageEntityDao.getEntityIdsForCoverageSet(coverageSetId.toString()).size
            updateProgress(PreparePhase.OSM, 40, message = "Saving OpenStreetMap features ($entityCount)…")
            coverageSetRepository.updateDownloadStatus(
                coverageSetId,
                DownloadStatus.DOWNLOADING,
                progressPct = 40,
                osmDatasetVersion = osmDatasetVersion,
                entityCount = entityCount
            )
            applyOsmAutoName(coverageSetId)

            cells.forEachIndexed { index, (tileLat, tileLon) ->
                checkCancelled()
                val tileId = DemTileId.fromCoordinates(tileLat, tileLon)
                val binFile = demTileRepository.demFile(tileId)
                if (demTileRepository.isBinReadable(tileId)) {
                    ensureTileRegistered(tileId, binFile)
                    acquireTileForCoverageSet(tileId)
                    val pct = 40 + ((index + 1) * 50 / cells.size.coerceAtLeast(1))
                    updateProgress(PreparePhase.DEM, pct, demTileIndex = index + 1, demTileCount = cells.size)
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
                                40 + (((index + tileFraction * 0.7) * 50) / cells.size.coerceAtLeast(1)).toInt()
                            updateProgress(
                                PreparePhase.DEM,
                                pct.coerceIn(40, 90),
                                demTileIndex = index + 1,
                                demTileCount = cells.size,
                                message = "Downloading DEM tile ${index + 1}/${cells.size}"
                            )
                        }
                    }

                    val conversion = geoTiffConverter.convert(tifFile, tileLat, tileLon, binFile)
                    if (!userPreferencesRepository.keepRawGeoTiff.first()) {
                        tifFile.delete()
                    }
                    upsertConvertedTile(tileId, tileLat, tileLon, conversion)
                    acquireTileForCoverageSet(tileId)
                    persistProgress(40 + ((index + 1) * 50 / cells.size.coerceAtLeast(1)))
                } catch (error: Exception) {
                    demFailures++
                    Log.w(TAG, "DEM tile failed for $tileId", error)
                    tifFile.delete()
                }
            }

            updateProgress(PreparePhase.POST_PROCESSING, 90, message = "Filling elevations…")
            persistProgress(90)
            val elevationFillOk =
                postProcessor.process(coverageSetId) { processed, total ->
                    val pct = 90 + ((processed * 9) / total.coerceAtLeast(1))
                    updateProgress(
                        PreparePhase.POST_PROCESSING,
                        pct.coerceIn(90, 99),
                        message = "Filling elevations ($processed/$total)…"
                    )
                }
            geoEntityRepository.garbageCollectOrphans()
            updateProgress(PreparePhase.POST_PROCESSING, 100, message = "Finalizing…")

            val terminalStatus =
                when {
                    demFailures > 0 -> DownloadStatus.PARTIAL
                    entityCount == 0 -> DownloadStatus.PARTIAL
                    !elevationFillOk -> DownloadStatus.PARTIAL
                    else -> DownloadStatus.READY
                }
            coverageSetRepository.updateDownloadStatus(coverageSetId, terminalStatus, progressPct = 100)

            try {
                enforceDemCacheLimit()
            } catch (_: Exception) {
                // Coverage set is already READY/PARTIAL; cache eviction is best-effort.
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Prepare download failed for coverage set $coverageSetId", error)
            _lastError.value = PrepareDownloadError.fromThrowable(error)
            val status =
                if (entityCount > 0 || demFailures > 0) DownloadStatus.PARTIAL else DownloadStatus.NOT_DOWNLOADED
            coverageSetRepository.updateDownloadStatus(
                coverageSetId,
                status,
                progressPct = _progress.value?.overallPercent ?: 0
            )
            Result.failure(error)
        } finally {
            if (activeCoverageSetId == coverageSetId) {
                _progress.value = null
                activeCoverageSetId = null
            }
        }
    }

    private suspend fun applyOsmAutoName(coverageSetId: UUID) {
        val coverageSet = coverageSetRepository.getCoverageSet(coverageSetId)
        if (coverageSet != null && !RegionNamePolicy.isUserProvidedName(coverageSet.name)) {
            val entityIds = coverageEntityDao.getEntityIdsForCoverageSet(coverageSetId.toString())
            val entities = entityIds.mapNotNull { entityId -> geoEntityRepository.getById(entityId) }
            val cellIds = coverageCellDao.getCellIdsForCoverageSet(coverageSetId.toString())
            val (referenceLat, referenceLon) = CoverageNameResolver.referenceCenterFromCellIds(cellIds)
            val chosenName = CoverageNameResolver.closestEntityName(entities, referenceLat, referenceLon)
            coverageSetRepository.updateCoverageSetName(coverageSetId, chosenName)
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
                refCount = existing?.refCount ?: 0,
                lastAccessedAt = Instant.now()
            )
        )
    }

    private suspend fun acquireTileForCoverageSet(tileId: String) {
        val owners = coverageCellDao.getCoverageSetIdsForCell(tileId).size
        val current = demTileRepository.getTile(tileId)?.refCount ?: return
        if (current < owners) {
            demTileRepository.incrementRefCount(tileId)
        }
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
        val coverageSetId = activeCoverageSetId ?: return
        _progress.value =
            PrepareProgress(
                coverageSetId = coverageSetId,
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
        val coverageSetId = activeCoverageSetId ?: return
        val pct = overallPercent.coerceIn(0, 100)
        if (pct >= lastPersistedPercent + 5 || pct == 0 || pct == 100) {
            lastPersistedPercent = pct
            coverageSetRepository.updateDownloadStatus(coverageSetId, DownloadStatus.DOWNLOADING, pct)
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
        const val MAX_OSM_ENTITIES_PER_CELL: Int = 50_000
        private const val OVERPASS_CELL_GAP_MS: Long = 1_000L
    }
}
