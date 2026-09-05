package com.nofar.core.model

import com.nofar.core.model.Glo90TileDimensions

/** Curated country packs: hardcoded 1° cell ids (not bbox polygons). */
data class CountryPack(val countryCode: String, val displayName: String, val cellIds: List<String>) {
    fun estimatedDiskBytes(): Long = Glo90TileDimensions.totalDiskBytes(cellIds.mapNotNull { DemTileId.parse(it) })
}

object CountryPackCatalog {
    /** Israel — approximate land coverage cells. */
    val ISRAEL =
        CountryPack(
            countryCode = "IL",
            displayName = "Israel",
            cellIds =
            listOf(
                "Copernicus_DSM_COG_30_N29_00_E034_00_DEM",
                "Copernicus_DSM_COG_30_N29_00_E035_00_DEM",
                "Copernicus_DSM_COG_30_N30_00_E034_00_DEM",
                "Copernicus_DSM_COG_30_N30_00_E035_00_DEM",
                "Copernicus_DSM_COG_30_N31_00_E034_00_DEM",
                "Copernicus_DSM_COG_30_N31_00_E035_00_DEM",
                "Copernicus_DSM_COG_30_N32_00_E034_00_DEM",
                "Copernicus_DSM_COG_30_N32_00_E035_00_DEM",
                "Copernicus_DSM_COG_30_N33_00_E035_00_DEM"
            )
        )

    /** Switzerland — approximate land coverage cells. */
    val SWITZERLAND =
        CountryPack(
            countryCode = "CH",
            displayName = "Switzerland",
            cellIds =
            listOf(
                "Copernicus_DSM_COG_30_N45_00_E006_00_DEM",
                "Copernicus_DSM_COG_30_N45_00_E007_00_DEM",
                "Copernicus_DSM_COG_30_N45_00_E008_00_DEM",
                "Copernicus_DSM_COG_30_N45_00_E009_00_DEM",
                "Copernicus_DSM_COG_30_N46_00_E006_00_DEM",
                "Copernicus_DSM_COG_30_N46_00_E007_00_DEM",
                "Copernicus_DSM_COG_30_N46_00_E008_00_DEM",
                "Copernicus_DSM_COG_30_N46_00_E009_00_DEM",
                "Copernicus_DSM_COG_30_N47_00_E006_00_DEM",
                "Copernicus_DSM_COG_30_N47_00_E007_00_DEM",
                "Copernicus_DSM_COG_30_N47_00_E008_00_DEM",
                "Copernicus_DSM_COG_30_N47_00_E009_00_DEM"
            )
        )

    /** Greece — approximate land coverage cells. */
    val GREECE =
        CountryPack(
            countryCode = "GR",
            displayName = "Greece",
            cellIds =
            listOf(
                "Copernicus_DSM_COG_30_N34_00_E022_00_DEM",
                "Copernicus_DSM_COG_30_N34_00_E023_00_DEM",
                "Copernicus_DSM_COG_30_N34_00_E024_00_DEM",
                "Copernicus_DSM_COG_30_N34_00_E025_00_DEM",
                "Copernicus_DSM_COG_30_N35_00_E022_00_DEM",
                "Copernicus_DSM_COG_30_N35_00_E023_00_DEM",
                "Copernicus_DSM_COG_30_N35_00_E024_00_DEM",
                "Copernicus_DSM_COG_30_N35_00_E025_00_DEM",
                "Copernicus_DSM_COG_30_N35_00_E026_00_DEM",
                "Copernicus_DSM_COG_30_N36_00_E022_00_DEM",
                "Copernicus_DSM_COG_30_N36_00_E023_00_DEM",
                "Copernicus_DSM_COG_30_N36_00_E024_00_DEM",
                "Copernicus_DSM_COG_30_N36_00_E025_00_DEM",
                "Copernicus_DSM_COG_30_N36_00_E029_00_DEM",
                "Copernicus_DSM_COG_30_N37_00_E022_00_DEM",
                "Copernicus_DSM_COG_30_N37_00_E023_00_DEM",
                "Copernicus_DSM_COG_30_N37_00_E024_00_DEM",
                "Copernicus_DSM_COG_30_N38_00_E020_00_DEM",
                "Copernicus_DSM_COG_30_N38_00_E021_00_DEM",
                "Copernicus_DSM_COG_30_N38_00_E022_00_DEM",
                "Copernicus_DSM_COG_30_N38_00_E023_00_DEM",
                "Copernicus_DSM_COG_30_N39_00_E020_00_DEM",
                "Copernicus_DSM_COG_30_N39_00_E021_00_DEM",
                "Copernicus_DSM_COG_30_N39_00_E022_00_DEM",
                "Copernicus_DSM_COG_30_N40_00_E020_00_DEM",
                "Copernicus_DSM_COG_30_N40_00_E021_00_DEM",
                "Copernicus_DSM_COG_30_N40_00_E022_00_DEM",
                "Copernicus_DSM_COG_30_N41_00_E020_00_DEM",
                "Copernicus_DSM_COG_30_N41_00_E021_00_DEM",
                "Copernicus_DSM_COG_30_N41_00_E022_00_DEM"
            )
        )

    val all: List<CountryPack> = listOf(ISRAEL, SWITZERLAND, GREECE)

    fun packsWithinByteBudget(cacheLimitBytes: Long): List<CountryPack> {
        val budget = (cacheLimitBytes * AppConfig.COVERAGE_BYTE_BUDGET_CACHE_FRACTION).toLong()
        return all.filter { pack -> pack.estimatedDiskBytes() <= budget }
    }

    fun packsOfferedAt(lat: Double, lon: Double, cacheLimitBytes: Long): List<CountryPack> {
        val cellId = CellMembership.cellIdForPoint(lat, lon)
        val budget = (cacheLimitBytes * AppConfig.COVERAGE_BYTE_BUDGET_CACHE_FRACTION).toLong()
        return all.filter { pack -> cellId in pack.cellIds && pack.estimatedDiskBytes() <= budget }
    }
}
