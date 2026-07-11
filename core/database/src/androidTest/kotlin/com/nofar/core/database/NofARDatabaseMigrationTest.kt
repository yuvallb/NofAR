package com.nofar.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NofARDatabaseMigrationTest {
    @Test
    fun freshInstall_createsRoomTablesAndRTree() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = TestDatabase.inMemory(context)
        GeoEntitySpatialQuery(
            db.geoEntitySpatialDao(),
            db.geoEntityDao(),
            db.regionEntityCoverageDao()
        ).backfillMissingRTreeEntries()
        assertThat(db.regionDao().getAll()).isEmpty()
        db.close()
    }
}
