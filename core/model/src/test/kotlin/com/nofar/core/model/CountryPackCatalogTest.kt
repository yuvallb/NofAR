package com.nofar.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CountryPackCatalogTest {
    @Test
    fun israelFitsDefaultCacheBudget() {
        val budget = (AppConfig.DEM_CACHE_DEFAULT_LIMIT_BYTES * AppConfig.COVERAGE_BYTE_BUDGET_CACHE_FRACTION).toLong()
        assertThat(CountryPackCatalog.ISRAEL.estimatedDiskBytes()).isAtMost(budget)
    }

    @Test
    fun greeceIncludesKastellorizo() {
        assertThat(CountryPackCatalog.GREECE.cellIds).contains("Copernicus_DSM_COG_30_N36_00_E029_00_DEM")
    }

    @Test
    fun packsWithinByteBudget_respectsCacheLimit() {
        val limit = 500L * 1024 * 1024
        val offered = CountryPackCatalog.packsWithinByteBudget(limit)
        assertThat(offered.map { it.countryCode }).containsAtLeast("IL", "CH")
        offered.forEach { pack ->
            assertThat(pack.estimatedDiskBytes()).isAtMost((limit * 0.5).toLong())
        }
    }

    @Test
    fun packsOfferedAt_returnsContainingPackWithinBudget() {
        val offered =
            CountryPackCatalog.packsOfferedAt(
                lat = 32.5,
                lon = 35.5,
                cacheLimitBytes = AppConfig.DEM_CACHE_DEFAULT_LIMIT_BYTES
            )

        assertThat(offered.map { it.countryCode }).contains("IL")
    }

    @Test
    fun packsOfferedAt_excludesPackOutsideBudget() {
        val offered =
            CountryPackCatalog.packsOfferedAt(
                lat = 32.5,
                lon = 35.5,
                cacheLimitBytes = 1L
            )

        assertThat(offered).isEmpty()
    }

    @Test
    fun packsOfferedAt_excludesPackNotContainingObserverCell() {
        val offered =
            CountryPackCatalog.packsOfferedAt(
                lat = 48.8,
                lon = 2.3,
                cacheLimitBytes = Long.MAX_VALUE
            )

        assertThat(offered).isEmpty()
    }

    @Test
    fun franceNotInCatalog() {
        assertThat(CountryPackCatalog.all.map { it.countryCode }).doesNotContain("FR")
    }
}
