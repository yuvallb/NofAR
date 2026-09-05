package com.nofar.feature.prepare

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.CellMembership
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import java.time.Instant
import java.util.UUID
import org.junit.Test

class VirtualLocationSelectionTest {
    private val observerLat = 32.5
    private val observerLon = 35.5
    private val observerCell = CellMembership.cellIdForPoint(observerLat, observerLon)

    private val readyCoverageSet =
        sampleCoverageSet(
            updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
            downloadStatus = DownloadStatus.READY
        )

    private val partialCoverageSet =
        sampleCoverageSet(
            id = UUID.randomUUID(),
            name = "Partial",
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            downloadStatus = DownloadStatus.PARTIAL
        )

    @Test
    fun resolveSelection_equalCells_prefersReadyThenNewest() {
        val cellIdsBySet =
            mapOf(
                partialCoverageSet.id to setOf(observerCell),
                readyCoverageSet.id to setOf(observerCell)
            )
        val selection =
            VirtualLocationSelectionLogic.resolveSelection(
                regions = listOf(partialCoverageSet, readyCoverageSet),
                cellIdsBySet = cellIdsBySet,
                lat = observerLat,
                lon = observerLon
            )
        assertThat(selection?.primaryCoverageSetId).isEqualTo(readyCoverageSet.id)
    }

    @Test
    fun resolveSelection_outsideCells_returnsNull() {
        val cellIdsBySet = mapOf(readyCoverageSet.id to setOf(observerCell))
        val selection =
            VirtualLocationSelectionLogic.resolveSelection(
                regions = listOf(readyCoverageSet),
                cellIdsBySet = cellIdsBySet,
                lat = 0.0,
                lon = 0.0
            )
        assertThat(selection).isNull()
    }

    @Test
    fun resolveSelection_includesContributingCoverageWithinQueryRadius() {
        val neighborLat = 33.2
        val neighborLon = 35.5
        val neighborCell = CellMembership.cellIdForPoint(neighborLat, neighborLon)
        val neighbor =
            sampleCoverageSet(
                id = UUID.randomUUID(),
                name = "Neighbor"
            )
        val cellIdsBySet =
            mapOf(
                readyCoverageSet.id to setOf(observerCell),
                neighbor.id to setOf(neighborCell)
            )
        val selection =
            VirtualLocationSelectionLogic.resolveSelection(
                regions = listOf(readyCoverageSet, neighbor),
                cellIdsBySet = cellIdsBySet,
                lat = observerLat,
                lon = observerLon
            )
        assertThat(selection?.contributingCoverageSetIds).containsExactly(readyCoverageSet.id, neighbor.id)
    }

    @Test
    fun resolveSelection_invalidCoordinate_returnsNull() {
        val cellIdsBySet = mapOf(readyCoverageSet.id to setOf(observerCell))
        assertThat(
            VirtualLocationSelectionLogic.resolveSelection(
                listOf(readyCoverageSet),
                cellIdsBySet,
                lat = 91.0,
                lon = 0.0
            )
        ).isNull()
    }

    private fun sampleCoverageSet(
        id: UUID = UUID.randomUUID(),
        name: String = "Ready",
        updatedAt: Instant = Instant.EPOCH,
        downloadStatus: DownloadStatus = DownloadStatus.READY
    ): CoverageSet = CoverageSet(
        id = id,
        name = name,
        createdAt = Instant.EPOCH,
        updatedAt = updatedAt,
        downloadStatus = downloadStatus,
        downloadProgressPct = 100,
        osmDatasetVersion = null,
        estimatedSizeBytes = 1L,
        entityCount = 1
    )
}
