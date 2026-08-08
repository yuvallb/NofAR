package com.nofar.core.database

import androidx.room.migration.Migration

/**
 * Explicit Room migrations from the public baseline ([NofARDatabase] version 1).
 *
 * Add a new [Migration] here before bumping `@Database(version = …)`.
 * Do not use destructive fallback on the production builder.
 */
object NofARDatabaseMigrations {
    val ALL: Array<Migration> = emptyArray()
}
