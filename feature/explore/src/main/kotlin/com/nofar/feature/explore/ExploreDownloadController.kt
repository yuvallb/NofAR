package com.nofar.feature.explore

import com.nofar.core.data.prepare.PrepareDownloadScheduler
import com.nofar.core.data.prepare.PrepareWorkState
import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.data.usecase.ExploreRegionResolution
import com.nofar.core.data.usecase.QuickRegionDownloadUseCase
import com.nofar.core.data.usecase.QuickRegionProposal
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ExploreDownloadController(
    private val scope: CoroutineScope,
    private val regionRepository: RegionRepository,
    private val quickRegionDownloadUseCase: QuickRegionDownloadUseCase,
    private val downloadScheduler: PrepareDownloadScheduler,
    private val uiState: MutableStateFlow<ExploreUiState>,
    private val onDownloadComplete: suspend (Region) -> Unit,
    private val onRefreshGate: () -> Unit
) {
    var pendingCellularProposal: QuickRegionProposal? = null
    private var observationJob: Job? = null

    suspend fun startDownload(proposal: QuickRegionProposal) {
        uiState.update {
            it.copy(
                downloadPromptDismissed = false,
                downloadUiMessage = null,
                downloadProgressPct = 0
            )
        }
        val result =
            quickRegionDownloadUseCase.createAndEnqueueAtLocation(
                centerLat = proposal.centerLat,
                centerLon = proposal.centerLon,
                radiusM = proposal.radiusM,
                name = proposal.name,
                existingRegionId = proposal.existingRegionId
            )
        result
            .onSuccess { regionId ->
                val region = regionRepository.getRegion(regionId)
                if (region != null) {
                    uiState.update {
                        it.copy(
                            regionResolution = ExploreRegionResolution.Downloading(region),
                            downloadPrompt = null
                        )
                    }
                    observeProgress(regionId)
                }
                onRefreshGate()
            }.onFailure { error ->
                uiState.update {
                    it.copy(downloadUiMessage = error.message ?: "Download failed. Try again.")
                }
            }
    }

    fun observeProgress(regionId: UUID) {
        observationJob?.cancel()
        observationJob =
            scope.launch {
                launch { pollProgress(regionId) }
                downloadScheduler.observeWorkState(regionId).collect { workState ->
                    handleWorkState(regionId, workState)
                }
            }
    }

    fun stopObservation() {
        observationJob?.cancel()
        observationJob = null
    }

    fun onCleared() {
        stopObservation()
    }

    private suspend fun pollProgress(regionId: UUID) {
        var keepPolling = true
        while (keepPolling) {
            val region = regionRepository.getRegion(regionId)
            if (region == null) {
                delay(DOWNLOAD_POLL_INTERVAL_MS)
                continue
            }
            uiState.update {
                it.copy(
                    downloadProgressPct = region.downloadProgressPct,
                    regionResolution = ExploreRegionResolution.Downloading(region)
                )
            }
            keepPolling =
                when (region.downloadStatus) {
                    DownloadStatus.READY, DownloadStatus.PARTIAL -> {
                        completeDownload(region)
                        false
                    }
                    DownloadStatus.DOWNLOADING -> {
                        delay(DOWNLOAD_POLL_INTERVAL_MS)
                        true
                    }
                    else -> {
                        failDownload()
                        false
                    }
                }
        }
    }

    private suspend fun handleWorkState(regionId: UUID, workState: PrepareWorkState?) {
        when (workState) {
            PrepareWorkState.SUCCEEDED -> {
                val region = regionRepository.getRegion(regionId)
                if (region != null &&
                    (
                        region.downloadStatus == DownloadStatus.READY ||
                            region.downloadStatus == DownloadStatus.PARTIAL
                        )
                ) {
                    completeDownload(region)
                }
            }
            PrepareWorkState.FAILED, PrepareWorkState.CANCELLED -> failDownload()
            else -> Unit
        }
    }

    private suspend fun completeDownload(region: Region) {
        onDownloadComplete(region)
        uiState.update {
            it.copy(
                regionResolution = ExploreRegionResolution.Active(region),
                downloadPrompt = null,
                downloadProgressPct = 100,
                downloadUiMessage = null
            )
        }
        onRefreshGate()
    }

    private fun failDownload() {
        uiState.update {
            it.copy(downloadUiMessage = "Download failed. Try again.")
        }
        onRefreshGate()
    }

    companion object {
        private const val DOWNLOAD_POLL_INTERVAL_MS = 500L
    }
}
