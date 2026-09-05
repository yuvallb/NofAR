@file:Suppress("ReturnCount")

package com.nofar.core.visibility

import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.model.CellMembership
import com.nofar.core.model.DemTileId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

fun interface DemPointElevationSource {
    suspend fun elevationAt(lat: Double, lon: Double, cellIds: Set<String>): Float?
}

@Singleton
class PointDemElevationLookup
@Inject
constructor(private val demTileRepository: DemTileRepository) :
    DemPointElevationSource {
    private var cachedTileId: String? = null
    private var cachedLatKey: Int? = null
    private var cachedLonKey: Int? = null
    private var cachedElevationM: Float? = null

    override suspend fun elevationAt(lat: Double, lon: Double, cellIds: Set<String>): Float? {
        if (!CellMembership.hasCell(cellIds, lat, lon)) return null
        val (tileLat, tileLon) = DemTileId.coordinatesForPoint(lat, lon)
        val tileId = DemTileId.fromCoordinates(tileLat, tileLon)
        if (tileId !in cellIds) return null

        val latKey = (lat * 1_000).roundToInt()
        val lonKey = (lon * 1_000).roundToInt()
        if (tileId == cachedTileId && latKey == cachedLatKey && lonKey == cachedLonKey) {
            return cachedElevationM
        }

        val reader = demTileRepository.openReader(tileId) ?: return null
        return try {
            reader.elevationAt(lat, lon)?.also { elevation ->
                cachedTileId = tileId
                cachedLatKey = latKey
                cachedLonKey = lonKey
                cachedElevationM = elevation
            }
        } finally {
            reader.close()
        }
    }
}
