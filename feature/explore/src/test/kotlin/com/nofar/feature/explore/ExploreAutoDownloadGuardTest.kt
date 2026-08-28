package com.nofar.feature.explore

import com.google.common.truth.Truth.assertThat
import com.nofar.core.data.usecase.QuickRegionProposal
import java.util.UUID
import org.junit.Test

class ExploreAutoDownloadGuardTest {
    private val guard = ExploreAutoDownloadGuard()

    private fun sampleProposal(
        lat: Double = 32.0,
        lon: Double = 35.0,
        existingRegionId: UUID? = null
    ): QuickRegionProposal = QuickRegionProposal(
        centerLat = lat,
        centerLon = lon,
        radiusM = 10_000.0,
        name = "Test",
        estimateBytes = 1L,
        demTileCount = 1,
        existingRegionId = existingRegionId
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
        val second = sampleProposal(lat = 33.0, lon = 36.0)
        guard.markAttempted(first)
        guard.onProposalChanged(second)
        assertThat(guard.shouldAttempt(second, forceRetry = false)).isTrue()
    }

    @Test
    fun proposalKey_usesExistingRegionIdWhenPresent() {
        val regionId = UUID.randomUUID()
        val proposal = sampleProposal(existingRegionId = regionId)
        assertThat(guard.proposalKey(proposal)).isEqualTo(regionId.toString())
    }
}
