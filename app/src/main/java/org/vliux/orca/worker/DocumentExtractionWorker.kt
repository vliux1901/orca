package org.vliux.orca.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.vliux.orca.logging.OrcaLog

class DocumentExtractionWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    private val deps: DocumentExtractionWorkerEntryPoint by lazy(LazyThreadSafetyMode.NONE) {
        EntryPointAccessors.fromApplication(
            applicationContext,
            DocumentExtractionWorkerEntryPoint::class.java,
        )
    }

    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID) ?: return Result.failure()
        OrcaLog.info("Worker started for document=${documentId.takeLast(8)}.")
        return when (deps.extractionProcessor().process(documentId)) {
            DocumentExtractionProcessor.ProcessingOutcome.Success -> Result.success()
            is DocumentExtractionProcessor.ProcessingOutcome.RetryableFailure -> Result.retry()
            is DocumentExtractionProcessor.ProcessingOutcome.TerminalFailure -> Result.failure()
        }
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"

        fun buildRequest(documentId: String) = OneTimeWorkRequestBuilder<DocumentExtractionWorker>()
            .setInputData(workDataOf(KEY_DOCUMENT_ID to documentId))
            .build()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DocumentExtractionWorkerEntryPoint {
    fun extractionProcessor(): DocumentExtractionProcessor
}
