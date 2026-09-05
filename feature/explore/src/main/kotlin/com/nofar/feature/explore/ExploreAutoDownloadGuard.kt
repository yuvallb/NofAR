package com.nofar.feature.explore

import com.nofar.core.data.usecase.QuickCoverageProposal

internal class ExploreAutoDownloadGuard {
    private var attemptedKey: String? = null
    private var declinedCellularKey: String? = null

    fun proposalKey(proposal: QuickCoverageProposal): String =
        proposal.cellIds.distinct().sorted().joinToString(separator = "|")

    fun onProposalChanged(proposal: QuickCoverageProposal) {
        val key = proposalKey(proposal)
        if (attemptedKey != null && attemptedKey != key) {
            attemptedKey = null
        }
        if (declinedCellularKey != null && declinedCellularKey != key) {
            declinedCellularKey = null
        }
    }

    fun shouldAttempt(proposal: QuickCoverageProposal, forceRetry: Boolean): Boolean {
        if (forceRetry) return true
        val key = proposalKey(proposal)
        return declinedCellularKey != key && attemptedKey != key
    }

    fun markAttempted(proposal: QuickCoverageProposal) {
        attemptedKey = proposalKey(proposal)
    }

    fun markCellularDeclined(proposal: QuickCoverageProposal) {
        declinedCellularKey = proposalKey(proposal)
        attemptedKey = proposalKey(proposal)
    }

    fun clearForRetry(proposal: QuickCoverageProposal) {
        val key = proposalKey(proposal)
        if (attemptedKey == key) attemptedKey = null
        if (declinedCellularKey == key) declinedCellularKey = null
    }

    fun clearOnSuccess(proposal: QuickCoverageProposal) {
        val key = proposalKey(proposal)
        if (attemptedKey == key) attemptedKey = null
        if (declinedCellularKey == key) declinedCellularKey = null
    }
}
