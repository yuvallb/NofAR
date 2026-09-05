package com.nofar.feature.explore

import com.google.common.truth.Truth.assertThat
import com.nofar.core.data.usecase.QuickCoverageProposal
import java.util.UUID
import org.junit.Test

class ExploreAutoDownloadGuardTest {
    private val guard = ExploreAutoDownloadGuard()

    private fun sampleProposal(
        lat: Double = 32.0,
        lon: Double = 35.0,
        cellIds: List<String> = listOf("N32E035"),
        existingCoverageSetId: UUID? = null
    ): QuickCoverageProposal = QuickCoverageProposal(
        centerLat = lat,
        centerLon = lon,
        cellIds = cellIds,
        name = "Test",
        estimateBytes = 1L,
        demTileCount = 1,
        existingCoverageSetId = existingCoverageSetId
    )

    @Test
    fun shouldAttempt_returnsTrueForFreshProposal() {
        assertThat(guard.shouldAttempt(sampleProposal(), forceRetry = false)).isTrue()
    }

    @Test
    fun markAttempted_blocksRepeatUntilRetry() {
        val proposal = sampleProposal()
        guard.markAttempted(proposal)
        assertThat(guard.shouldAttempt(proposal, forceRetry = false)).isFalse()
        guard.clearForRetry(proposal)
        assertThat(guard.shouldAttempt(proposal, forceRetry = false)).isTrue()
    }

    @Test
    fun markCellularDeclined_blocksUntilRetry() {
        val proposal = sampleProposal()
        guard.markCellularDeclined(proposal)
        assertThat(guard.shouldAttempt(proposal, forceRetry = false)).isFalse()
        guard.clearForRetry(proposal)
        assertThat(guard.shouldAttempt(proposal, forceRetry = false)).isTrue()
    }

    @Test
    fun onProposalChanged_clearsGuardForNewLocation() {
        val first = sampleProposal(lat = 32.0, lon = 35.0)
        val second = sampleProposal(lat = 33.0, lon = 36.0, cellIds = listOf("N33E036"))
        guard.markAttempted(first)
        guard.onProposalChanged(second)
        assertThat(guard.shouldAttempt(second, forceRetry = false)).isTrue()
    }

    @Test
    fun proposalKey_usesSortedCellSet() {
        val coverageSetId = UUID.randomUUID()
        val proposal =
            sampleProposal(
                cellIds = listOf("N33E036", "N32E035", "N32E035"),
                existingCoverageSetId = coverageSetId
            )
        assertThat(guard.proposalKey(proposal)).isEqualTo("N32E035|N33E036")
    }

    @Test
    fun sameCellsAtDifferentLocation_remainsBlocked() {
        val first = sampleProposal(lat = 32.1, lon = 35.1)
        val movedWithinCell = sampleProposal(lat = 32.8, lon = 35.8)
        guard.markAttempted(first)
        guard.onProposalChanged(movedWithinCell)

        assertThat(guard.shouldAttempt(movedWithinCell, forceRetry = false)).isFalse()
    }
}
