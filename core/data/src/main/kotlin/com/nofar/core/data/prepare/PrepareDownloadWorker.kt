package com.nofar.core.data.prepare

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.util.UUID

@HiltWorker
class PrepareDownloadWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val orchestrator: PrepareDownloadOrchestrator
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val coverageSetId =
            inputData.getString(KEY_COVERAGE_SET_ID)?.let(UUID::fromString)
                ?: return androidx.work.ListenableWorker.Result.failure()

        return orchestrator.download(coverageSetId).fold(
            onSuccess = { androidx.work.ListenableWorker.Result.success() },
            onFailure = { error ->
                if (error is IOException && runAttemptCount < MAX_ATTEMPTS) {
                    androidx.work.ListenableWorker.Result.retry()
                } else {
                    androidx.work.ListenableWorker.Result.failure()
                }
            }
        )
    }

    companion object {
        const val KEY_COVERAGE_SET_ID = "coverage_set_id"
        const val UNIQUE_WORK_PREFIX = "prepare_download_"
        private const val MAX_ATTEMPTS = 3
    }
}
