package com.nofar.core.data.prepare

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class PrepareWorkState {
    ENQUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    UNKNOWN
}

@Singleton
class PrepareDownloadScheduler
@Inject
constructor(@ApplicationContext private val context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueue(regionId: UUID) {
        val request =
            OneTimeWorkRequestBuilder<PrepareDownloadWorker>()
                .setInputData(workDataOf(PrepareDownloadWorker.KEY_REGION_ID to regionId.toString()))
                .build()
        workManager.enqueueUniqueWork(
            "${PrepareDownloadWorker.UNIQUE_WORK_PREFIX}$regionId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(regionId: UUID) {
        workManager.cancelUniqueWork("${PrepareDownloadWorker.UNIQUE_WORK_PREFIX}$regionId")
    }

    fun observeWorkState(regionId: UUID): Flow<PrepareWorkState?> = workManager
        .getWorkInfosForUniqueWorkFlow("${PrepareDownloadWorker.UNIQUE_WORK_PREFIX}$regionId")
        .map { infos -> infos.firstOrNull()?.state?.toPrepareWorkState() }

    private fun WorkInfo.State.toPrepareWorkState(): PrepareWorkState = when (this) {
        WorkInfo.State.ENQUEUED -> PrepareWorkState.ENQUEUED
        WorkInfo.State.RUNNING -> PrepareWorkState.RUNNING
        WorkInfo.State.SUCCEEDED -> PrepareWorkState.SUCCEEDED
        WorkInfo.State.FAILED -> PrepareWorkState.FAILED
        WorkInfo.State.CANCELLED -> PrepareWorkState.CANCELLED
        WorkInfo.State.BLOCKED -> PrepareWorkState.ENQUEUED
        else -> PrepareWorkState.UNKNOWN
    }
}
