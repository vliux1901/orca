package org.vliux.orca.worker

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.extraction.ExtractionPipelineException
import org.vliux.orca.extraction.ExtractionRequest
import org.vliux.orca.extraction.MaintenanceExtractionEngine
import org.vliux.orca.extraction.SourcePayload
import org.vliux.orca.importing.AppFileStorage
import org.vliux.orca.logging.OrcaLog
import org.vliux.orca.normalization.ExtractionNormalizer
import org.vliux.orca.provider.ProviderRuntimeConfig

@Singleton
class DocumentExtractionProcessor @Inject constructor(
    private val repository: DocumentRepository,
    private val providerRuntimeConfig: ProviderRuntimeConfig,
    private val extractionEngine: MaintenanceExtractionEngine,
    private val extractionNormalizer: ExtractionNormalizer,
    private val fileStorage: AppFileStorage,
) {
    suspend fun process(
        documentId: String,
    ): ProcessingOutcome {
        val bundle = repository.getDocumentBundle(documentId) ?: return ProcessingOutcome.TerminalFailure("DOCUMENT_NOT_FOUND")
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
                result = normalized,
            )
            OrcaLog.info("Worker finished successfully for document=${documentId.takeLast(8)}.")
            ProcessingOutcome.Success
        }.getOrElse { error ->
            val failure = classify(error)
            OrcaLog.error(
                "Worker failed for document=${documentId.takeLast(8)} with ${error::class.java.simpleName}: ${error.message}",
                error,
            )
            if (failure.retryable) {
                repository.markRunRetryableFailure(
                    runId = run.runId,
                    errorCode = failure.errorCode,
                )
                ProcessingOutcome.RetryableFailure(failure.errorCode)
            } else {
                repository.markRunFailed(
                    runId = run.runId,
                    errorCode = failure.errorCode,
                )
                ProcessingOutcome.TerminalFailure(failure.errorCode)
            }
        }
    }

    private fun classify(
        error: Throwable,
    ): ClassifiedFailure {
        return when (error) {
            is ExtractionPipelineException -> ClassifiedFailure(
                errorCode = error.errorCode,
                retryable = error.retryable,
            )

            is IOException -> ClassifiedFailure(
                errorCode = "PROVIDER_IO",
                retryable = true,
            )

            else -> ClassifiedFailure(
                errorCode = "EXTRACTION_PIPELINE_ERROR",
                retryable = false,
            )
        }
    }

    data class ClassifiedFailure(
        val errorCode: String,
        val retryable: Boolean,
    )

    sealed interface ProcessingOutcome {
        data object Success : ProcessingOutcome
        data class RetryableFailure(val errorCode: String) : ProcessingOutcome
        data class TerminalFailure(val errorCode: String) : ProcessingOutcome
    }

    companion object {
        const val PROMPT_VERSION = "phase3-v1"
    }
}
