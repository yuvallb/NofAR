package com.nofar.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Android's platform SQLite often lacks the R-Tree module. Bundled SQLite includes it.
 */
fun <T : RoomDatabase> RoomDatabase.Builder<T>.useBundledSqliteWithRTree(): RoomDatabase.Builder<T> =
    setDriver(BundledSQLiteDriver())
        .addCallback(RTreeCallback())
