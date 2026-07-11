package com.nofar.core.database

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class RTreeMaintenance
@Inject
constructor(private val spatialQuery: GeoEntitySpatialQuery) {
    private val backfillInFlight = AtomicBoolean(false)

    suspend fun backfillMissingEntriesIfNeeded() {
        if (!backfillInFlight.compareAndSet(false, true)) return
        try {
            withContext(Dispatchers.IO) {
                spatialQuery.backfillMissingRTreeEntries()
            }
        } finally {
            backfillInFlight.set(false)
        }
    }
}
