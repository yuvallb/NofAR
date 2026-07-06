package com.nofar.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NofARDatabaseMigrationTest {
    @Test
    fun freshInstall_createsRoomTablesAndRTree() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = TestDatabase.inMemory(context)
        db.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE name = 'geo_entity_rtree'"
        ).use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
        }
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM region").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
        }
        db.close()
    }
}
