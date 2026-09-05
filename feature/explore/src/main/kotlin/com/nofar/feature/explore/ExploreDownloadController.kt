package com.nofar.feature.explore

import com.nofar.core.data.prepare.PrepareDownloadScheduler
import com.nofar.core.data.prepare.PrepareWorkState
import com.nofar.core.data.repository.CoverageSetRepository
import com.nofar.core.data.usecase.ExploreCoverageResolution
import com.nofar.core.data.usecase.QuickCoverageDownloadUseCase
import com.nofar.core.data.usecase.QuickCoverageProposal
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Starts Simple Mode nearby-region downloads via [PrepareDownloadScheduler] (WorkManager)
 * and keeps Explore on-screen while progress is observed. HTTP still runs in the Prepare worker.
 */
internal class ExploreDownloadController(
    private val scope: CoroutineScope,
    private val coverageSetRepository: CoverageSetRepository,
    private val quickCoverageDownloadUseCase: QuickCoverageDownloadUseCase,
    private val downloadScheduler: PrepareDownloadScheduler,
    private val uiState: MutableStateFlow<ExploreUiState>,
    private val onDownloadComplete: suspend (CoverageSet) -> Unit,
    private val onRefreshGate: () -> Unit
) {
    var pendingCellularProposal: QuickCoverageProposal? = null
    private var observationJob: Job? = null
    private var activeProposal: QuickCoverageProposal? = null

    suspend fun startDownload(proposal: QuickCoverageProposal) {
        activeProposal = proposal
        uiState.update {
            it.copy(
                downloadUiMessage = null,
                downloadProgressPct = 0
            )
        }
        val result =
            quickCoverageDownloadUseCase.createAndEnqueueAtLocation(
                centerLat = proposal.centerLat,
                centerLon = proposal.centerLon,
                name = proposal.name,
                existingCoverageSetId = proposal.existingCoverageSetId,
                cellIds = proposal.cellIds
            )
        result
            .onSuccess { coverageSetId ->
                val region = coverageSetRepository.getCoverageSet(coverageSetId)
                if (region != null) {
                    uiState.update {
                        it.copy(
                            regionResolution = ExploreCoverageResolution.Downloading(region),
                            downloadPrompt = null
                        )
                    }
                    observeProgress(coverageSetId)
                }
                onRefreshGate()
            }.onFailure { error ->
                failDownload(error.message ?: "Download failed. Try again.")
            }
    }

    fun observeProgress(coverageSetId: UUID) {
        observationJob?.cancel()
        observationJob =
            scope.launch {
                launch { pollProgress(coverageSetId) }
                downloadScheduler.observeWorkState(coverageSetId).collect { workState ->
                    handleWorkState(coverageSetId, workState)
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

    private suspend fun pollProgress(coverageSetId: UUID) {
        var keepPolling = true
        while (keepPolling) {
            val region = coverageSetRepository.getCoverageSet(coverageSetId)
            if (region == null) {
                delay(DOWNLOAD_POLL_INTERVAL_MS)
                continue
            }
            uiState.update {
                it.copy(
                    downloadProgressPct = region.downloadProgressPct,
                    regionResolution = ExploreCoverageResolution.Downloading(region)
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

    private suspend fun handleWorkState(coverageSetId: UUID, workState: PrepareWorkState?) {
        when (workState) {
            PrepareWorkState.SUCCEEDED -> {
                val region = coverageSetRepository.getCoverageSet(coverageSetId)
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

    private suspend fun completeDownload(region: CoverageSet) {
        onDownloadComplete(region)
        activeProposal = null
        uiState.update {
            it.copy(
                regionResolution = ExploreCoverageResolution.Active(region),
                downloadPrompt = null,
                downloadProgressPct = 100,
                downloadUiMessage = null
            )
        }
        onRefreshGate()
    }

    private fun failDownload(message: String = "Download failed. Try again.") {
        val proposal = activeProposal ?: uiState.value.downloadPrompt
        uiState.update { state ->
            if (proposal == null) {
                state.copy(downloadUiMessage = message, downloadProgressPct = 0)
            } else {
                state.copy(
                    regionResolution = ExploreCoverageResolution.NeedsDownload(proposal),
                    downloadPrompt = proposal,
                    downloadProgressPct = 0,
                    downloadUiMessage = message
                )
            }
        }
        onRefreshGate()
    }

    companion object {
        private const val DOWNLOAD_POLL_INTERVAL_MS = 500L
    }
}
