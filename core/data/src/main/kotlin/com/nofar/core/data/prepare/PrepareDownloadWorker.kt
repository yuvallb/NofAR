package com.nofar.core.data.prepare

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
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
        val regionId =
            inputData.getString(KEY_REGION_ID)?.let(UUID::fromString)
                ?: return androidx.work.ListenableWorker.Result.failure()

        return orchestrator.download(regionId).fold(
            onSuccess = { androidx.work.ListenableWorker.Result.success() },
            onFailure = { androidx.work.ListenableWorker.Result.retry() }
        )
    }

    companion object {
        const val KEY_REGION_ID = "region_id"
        const val UNIQUE_WORK_PREFIX = "prepare_download_"
    }
}
