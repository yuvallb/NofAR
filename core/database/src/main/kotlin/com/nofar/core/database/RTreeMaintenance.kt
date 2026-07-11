package com.nofar.core.database

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class RTreeMaintenance
@Inject
constructor(private val database: NofARDatabase) {
    private val backfillAttempted = AtomicBoolean(false)

    suspend fun backfillMissingEntriesIfNeeded() {
        if (!backfillAttempted.compareAndSet(false, true)) return
        withContext(Dispatchers.IO) {
            RTreeCallback.backfillMissingEntriesSafely(database.openHelper.writableDatabase)
        }
    }
}
