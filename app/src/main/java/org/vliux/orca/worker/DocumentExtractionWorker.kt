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
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.extraction.MaintenanceExtractionEngine
import org.vliux.orca.logging.OrcaLog
import org.vliux.orca.extraction.ExtractionRequest
import org.vliux.orca.extraction.SourcePayload
import org.vliux.orca.importing.AppFileStorage
import org.vliux.orca.normalization.ExtractionNormalizer
import org.vliux.orca.provider.ProviderRuntimeConfig

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
        val repository = deps.documentRepository()
        val providerRuntimeConfig = deps.providerRuntimeConfig()
        val extractionEngine = deps.extractionEngine()
        val extractionNormalizer = deps.extractionNormalizer()
        val fileStorage = deps.fileStorage()
        val bundle = repository.getDocumentBundle(documentId) ?: return Result.failure()
        val run = repository.createQueuedRun(
            documentId = documentId,
            providerName = providerRuntimeConfig.providerName,
            modelName = providerRuntimeConfig.modelName,
            promptVersion = PROMPT_VERSION,
        )
        return runCatching {
            repository.markRunRunning(run.runId)
            val request = ExtractionRequest(
                document = bundle.document,
                sources = bundle.sources.map { source ->
                    SourcePayload(
                        source = source,
                        file = fileStorage.resolveFile(source.relativePath),
                    )
                },
                promptVersion = PROMPT_VERSION,
            )
            OrcaLog.info(
                "Sending ${request.sources.size} source file(s) to OpenAI for document=${documentId.takeLast(8)}.",
            )
            val parsed = extractionEngine.extract(request)
            OrcaLog.info(
                "Received structured extraction payload for document=${documentId.takeLast(8)} with ${parsed.response.facts.size} fact(s).",
            )
            val normalized = extractionNormalizer.normalize(
                documentId = bundle.document.documentId,
                vehicleId = bundle.document.vehicleId,
                runId = run.runId,
                response = parsed.response,
                sources = bundle.sources,
            )
            repository.applyExtractionResult(
                documentId = documentId,
                runId = run.runId,
                parsed = parsed,
                result = normalized,
            )
            OrcaLog.info("Worker finished successfully for document=${documentId.takeLast(8)}.")
            Result.success()
        }.getOrElse { error ->
            OrcaLog.error(
                "Worker failed for document=${documentId.takeLast(8)} with ${error::class.java.simpleName}: ${error.message}",
                error,
            )
            repository.markRunFailed(
                runId = run.runId,
                errorCode = error::class.java.simpleName,
            )
            Result.failure()
        }
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        const val PROMPT_VERSION = "phase0-v1"

        fun buildRequest(documentId: String) = OneTimeWorkRequestBuilder<DocumentExtractionWorker>()
            .setInputData(workDataOf(KEY_DOCUMENT_ID to documentId))
            .build()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DocumentExtractionWorkerEntryPoint {
    fun documentRepository(): DocumentRepository
    fun extractionEngine(): MaintenanceExtractionEngine
    fun extractionNormalizer(): ExtractionNormalizer
    fun providerRuntimeConfig(): ProviderRuntimeConfig
    fun fileStorage(): AppFileStorage
}
