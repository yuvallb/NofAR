package com.nofar.core.data.usecase

import android.content.Context
import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.data.preferences.UserPreferencesRepository
import com.nofar.core.data.repository.CoverageSetRepository
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.model.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * One-shot upgrade: delete legacy (non-v4) DEM rasters and mark all coverage PARTIAL so users
 * re-download GLO-90 int16 tiles.
 */
@Singleton
class DemRasterUpgradeUseCase
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val demTileDao: DemTileDao,
    private val demTileRepository: DemTileRepository,
    private val coverageSetRepository: CoverageSetRepository
) {
    suspend fun ensureApplied(): Boolean {
        if (userPreferencesRepository.demV4UpgradeApplied.first()) return false

        val demDirectory = File(context.filesDir, "dem").also { it.mkdirs() }
        var wipedAny = false

        demDirectory.listFiles()?.filter { it.extension == "bin" }?.forEach { file ->
            if (!DemTileReader.hasCurrentFormat(file)) {
                file.delete()
                wipedAny = true
            }
        }

        demTileDao.getAll().forEach { entity ->
            val file = File(context.filesDir, entity.filePath)
            if (!file.exists() || !DemTileReader.hasCurrentFormat(file)) {
                demTileRepository.evictTile(entity.tileId)
                wipedAny = true
            }
        }

        val coverageSets = coverageSetRepository.observeAllCoverageSets().first()
        coverageSets.forEach { coverageSet ->
            if (coverageSet.downloadStatus == DownloadStatus.READY ||
                coverageSet.downloadStatus == DownloadStatus.DOWNLOADING
            ) {
                coverageSetRepository.updateDownloadStatus(
                    id = coverageSet.id,
                    status = DownloadStatus.PARTIAL,
                    progressPct = 0
                )
                wipedAny = true
            }
        }

        userPreferencesRepository.markDemV4UpgradeApplied()
        return wipedAny
    }
}
