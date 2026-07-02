package org.vliux.orca.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class PhaseZeroNoOpWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        return Result.success(
            workDataOf(KEY_WORK_STATUS to WORK_STATUS_COMPLETE),
        )
    }

    companion object {
        const val KEY_WORK_STATUS = "work_status"
        const val WORK_STATUS_COMPLETE = "complete"

        fun buildRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<PhaseZeroNoOpWorker>().build()
    }
}
