package com.nofar.core.data.usecase

import com.nofar.core.data.preferences.UserPreferencesRepository
import com.nofar.core.data.prepare.PrepareDownloadScheduler
import com.nofar.core.data.prepare.PrepareEstimator
import com.nofar.core.data.prepare.RegionNamePolicy
import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.model.AppConfig
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class QuickRegionDownloadUseCase
@Inject
constructor(
    private val regionRepository: RegionRepository,
    private val downloadScheduler: PrepareDownloadScheduler,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun syncAndEnqueue(region: Region): Result<UUID> = runCatching {
        val existing = regionRepository.getRegion(region.id)
        if (existing == null) {
            regionRepository.createRegion(region)
        } else {
            regionRepository.updateRegion(region)
        }
        downloadScheduler.enqueue(region.id)
        region.id
    }

    suspend fun createAndEnqueueAtLocation(
        centerLat: Double,
        centerLon: Double,
        radiusM: Double = AppConfig.SIMPLE_MODE_DEFAULT_RADIUS_M,
        name: String = RegionNamePolicy.formatAutoName(centerLat, centerLon),
        existingRegionId: UUID? = null
    ): Result<UUID> {
        val regionId = existingRegionId ?: UUID.randomUUID()
        val now = Instant.now()
        val bbox = RegionBounds.boundingBox(centerLat, centerLon, radiusM)
        val estimate = PrepareEstimator.estimate(centerLat, centerLon, radiusM)
        val existing = regionRepository.getRegion(regionId)
        val labelLanguage = userPreferencesRepository.preferredLabelLanguage.first()
        val region =
            Region(
                id = regionId,
                name = name.trim(),
                centerLat = centerLat,
                centerLon = centerLon,
                radiusM = radiusM,
                minLat = bbox.minLat,
                maxLat = bbox.maxLat,
                minLon = bbox.minLon,
                maxLon = bbox.maxLon,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                downloadStatus = existing?.downloadStatus ?: DownloadStatus.NOT_DOWNLOADED,
                downloadProgressPct = existing?.downloadProgressPct ?: 0,
                osmDatasetVersion = existing?.osmDatasetVersion,
                estimatedSizeBytes = estimate.totalEstimateBytes,
                entityCount = existing?.entityCount ?: 0,
                labelLanguage = labelLanguage
            )
        return syncAndEnqueue(region)
    }
}
