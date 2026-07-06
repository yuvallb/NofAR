package com.nofar.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nofar.core.database.model.GeoEntityEntity
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.ResolutionLevel
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeoEntityRTreePerformanceTest {
    private lateinit var fixtures: DatabaseTestFixtures

    @Before
    fun setUp() {
        fixtures = DatabaseTestFixtures(TestDatabase.inMemory())
    }

    @After
    fun tearDown() {
        fixtures.database.close()
    }

    @Test
    fun radiusQuery_oneThousandEntities_completesUnderFiftyMs() = runTest {
        repeat(1_000) { index ->
            fixtures.geoEntityUpserter.upsert(
                GeoEntityEntity(
                    id = "node/$index",
                    osmType = "NODE",
                    name = "Place $index",
                    type = GeoEntityType.TOWN.name,
                    lat = 32.0 + (index % 100) * 0.001,
                    lon = 35.0 + (index / 100) * 0.001,
                    elevation = 200.0,
                    elevationSource = "OSM_TAG",
                    lastSeenAt = System.currentTimeMillis()
                )
            )
        }

        val elapsed =
            measureTimeMillis {
                val results =
                    fixtures.spatialQuery.queryWithinRadius(
                        lat = 32.05,
                        lon = 35.05,
                        radiusM = 5_000.0,
                        resolutionLevel = ResolutionLevel.Full
                    )
                assertThat(results).isNotEmpty()
            }
        assertThat(elapsed).isLessThan(50L)
    }
}
