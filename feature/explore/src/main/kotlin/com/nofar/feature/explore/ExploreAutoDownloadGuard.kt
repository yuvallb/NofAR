package com.nofar.feature.explore

import com.nofar.core.data.usecase.QuickRegionProposal

/**
 * Prevents Simple Mode auto-download from re-firing on every GPS tick for the same proposal.
 * Cleared on Retry, successful start, or when the proposal identity changes.
 */
internal class ExploreAutoDownloadGuard {
    private var attemptedKey: String? = null
    private var declinedCellularKey: String? = null

    fun proposalKey(proposal: QuickRegionProposal): String = proposal.existingRegionId?.toString()
        ?: "${proposal.centerLat},${proposal.centerLon},${proposal.radiusM}"

    fun onProposalChanged(proposal: QuickRegionProposal) {
        val key = proposalKey(proposal)
        if (attemptedKey != null && attemptedKey != key) {
            attemptedKey = null
        }
        if (declinedCellularKey != null && declinedCellularKey != key) {
            declinedCellularKey = null
        }
    }

    fun shouldAttempt(proposal: QuickRegionProposal, forceRetry: Boolean): Boolean {
        if (forceRetry) return true
        val key = proposalKey(proposal)
        return declinedCellularKey != key && attemptedKey != key
    }

    fun markAttempted(proposal: QuickRegionProposal) {
        attemptedKey = proposalKey(proposal)
    }

    fun markCellularDeclined(proposal: QuickRegionProposal) {
        declinedCellularKey = proposalKey(proposal)
        attemptedKey = proposalKey(proposal)
    }

    fun clearForRetry(proposal: QuickRegionProposal) {
        val key = proposalKey(proposal)
        if (attemptedKey == key) attemptedKey = null
        if (declinedCellularKey == key) declinedCellularKey = null
    }

    fun clearOnSuccess(proposal: QuickRegionProposal) {
        val key = proposalKey(proposal)
        if (attemptedKey == key) attemptedKey = null
        if (declinedCellularKey == key) declinedCellularKey = null
    }
}
