package org.vliux.orca.data

import androidx.room.withTransaction
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.vliux.orca.logging.OrcaLog
import org.vliux.orca.extraction.ParsedExtractionPayload
import org.vliux.orca.importing.AppFileStorage
import org.vliux.orca.importing.DocumentImportPlanner
import org.vliux.orca.importing.SourceImportPlanInput
import org.vliux.orca.importing.SourceImportOrdering
import org.vliux.orca.importing.SourceImportRequest
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentImportStatus
import org.vliux.orca.data.model.DocumentListItem
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.data.model.ExtractionRunEntity
import org.vliux.orca.data.model.ExtractionStatus
import org.vliux.orca.data.model.MaintenanceEventEntity
import org.vliux.orca.data.model.ServiceItemEntity
import org.vliux.orca.normalization.NormalizedExtractionResult

class DocumentRepository(
    private val database: OrcaDatabase,
    private val fileStorage: AppFileStorage,
    private val planner: DocumentImportPlanner = DocumentImportPlanner(),
) {
    private val dao = database.documentDao()
    private val json = Json { prettyPrint = true; explicitNulls = false }

    suspend fun importDocument(
        vehicleId: String,
        imports: List<SourceImportRequest>,
    ): String {
        require(imports.isNotEmpty()) { "At least one import is required." }
        val orderedImports = SourceImportOrdering.order(imports)

        val plan = planner.plan(
            vehicleId = vehicleId,
            imports = orderedImports.map { SourceImportPlanInput(it.displayName, it.mimeType) },
        )

        val storedSources = plan.sources.zip(orderedImports).map { (sourcePlan, request) ->
            fileStorage.copySource(sourcePlan.source, request)
        }

        database.withTransaction {
            dao.insertDocument(plan.document)
            dao.insertSources(storedSources.map { it.source })
            dao.updateDocumentState(
                documentId = plan.document.documentId,
                sourceCount = storedSources.size,
                importStatus = DocumentImportStatus.READY.name,
                extractionStatus = ExtractionStatus.NOT_STARTED.name,
                latestRunId = null,
                occurredAt = null,
                updatedAt = plan.document.updatedAt,
            )
        }

        OrcaLog.info(
            "Stored document=${plan.document.documentId.takeLast(8)} locally with ${storedSources.size} ordered source file(s).",
        )

        return plan.document.documentId
    }

    fun observeDocumentList(vehicleId: String): Flow<List<DocumentListItem>> =
        dao.observeDocumentList(vehicleId)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDocumentDetail(documentId: String): Flow<DocumentDetail?> {
        val eventFlow = dao.observeEvent(documentId)
        val documentBundleFlow = combine(
            dao.observeDocument(documentId),
            dao.observeSources(documentId),
            dao.observeLatestRun(documentId),
        ) { document, sources, latestRun ->
            Triple(document, sources, latestRun)
        }
        val extractionFlow = combine(
            dao.observeActiveFacts(documentId),
            eventFlow,
            eventFlow.flatMapLatest { event ->
                event?.let { dao.observeServiceItems(it.eventId) } ?: flowOf(emptyList())
            },
        ) { facts, event, serviceItems ->
            Triple(facts, event, serviceItems)
        }
        return combine(documentBundleFlow, extractionFlow) { documentBundle, extractionBundle ->
            val (document, sources, latestRun) = documentBundle
            val (facts, event, serviceItems) = extractionBundle
            document?.let {
                DocumentDetail(
                    document = it,
                    sources = sources,
                    latestRun = latestRun,
                    facts = facts,
                    event = event,
                    serviceItems = serviceItems,
                )
            }
        }
    }

    suspend fun getDocument(documentId: String): DocumentEntity? =
        dao.getDocument(documentId)

    suspend fun getSources(documentId: String): List<DocumentSourceEntity> =
        dao.getSources(documentId)

    suspend fun getDocumentBundle(documentId: String): DocumentBundle? {
        val document = dao.getDocument(documentId) ?: return null
        return DocumentBundle(
            document = document,
            sources = dao.getSources(documentId),
        )
    }

    suspend fun markDocumentQueued(documentId: String) {
        val document = requireNotNull(dao.getDocument(documentId))
        val now = Instant.now().toString()
        dao.updateDocumentState(
            documentId = documentId,
            sourceCount = document.sourceCount,
            importStatus = document.importStatus,
            extractionStatus = ExtractionStatus.QUEUED.name,
            latestRunId = document.latestRunId,
            occurredAt = document.occurredAt,
            updatedAt = now,
        )
        OrcaLog.info("Marked document=${documentId.takeLast(8)} extraction as QUEUED.")
    }

    suspend fun createQueuedRun(
        documentId: String,
        providerName: String,
        modelName: String,
        promptVersion: String,
    ): ExtractionRunEntity {
        val document = requireNotNull(dao.getDocument(documentId))
        val now = Instant.now().toString()
        val run = ExtractionRunEntity(
            runId = UUID.randomUUID().toString(),
            documentId = documentId,
            backendType = "CLOUD_VISION",
            providerName = providerName,
            modelName = modelName,
            promptVersion = promptVersion,
            status = ExtractionStatus.QUEUED.name,
            errorCode = null,
            requestPrompt = null,
            responseEnvelopeJson = null,
            responseOutputJson = null,
            normalizationDebugJson = null,
            startedAt = null,
            completedAt = null,
            createdAt = now,
        )

        database.withTransaction {
            dao.insertExtractionRun(run)
            dao.updateDocumentState(
                documentId = documentId,
                sourceCount = document.sourceCount,
                importStatus = document.importStatus,
                extractionStatus = ExtractionStatus.QUEUED.name,
                latestRunId = run.runId,
                occurredAt = document.occurredAt,
                updatedAt = now,
            )
        }
        OrcaLog.info(
            "Created extraction run=${run.runId.takeLast(8)} for document=${documentId.takeLast(8)} model=$modelName.",
        )
        return run
    }

    suspend fun markRunRunning(runId: String) {
        val run = requireNotNull(dao.getRun(runId))
        val document = requireNotNull(dao.getDocument(run.documentId))
        val now = Instant.now().toString()
        database.withTransaction {
            dao.updateExtractionRun(
                runId = runId,
                status = ExtractionStatus.RUNNING.name,
                errorCode = null,
                startedAt = now,
                completedAt = null,
            )
            dao.updateDocumentState(
                documentId = run.documentId,
                sourceCount = document.sourceCount,
                importStatus = document.importStatus,
                extractionStatus = ExtractionStatus.RUNNING.name,
                latestRunId = runId,
                occurredAt = document.occurredAt,
                updatedAt = now,
            )
        }
        OrcaLog.info("Marked extraction run=${runId.takeLast(8)} as RUNNING.")
    }

    suspend fun markRunFailed(
        runId: String,
        errorCode: String,
    ) {
        val run = requireNotNull(dao.getRun(runId))
        val document = requireNotNull(dao.getDocument(run.documentId))
        val now = Instant.now().toString()
        database.withTransaction {
            dao.updateExtractionRun(
                runId = runId,
                status = ExtractionStatus.FAILED.name,
                errorCode = errorCode,
                startedAt = null,
                completedAt = now,
            )
            dao.updateDocumentState(
                documentId = run.documentId,
                sourceCount = document.sourceCount,
                importStatus = document.importStatus,
                extractionStatus = ExtractionStatus.FAILED.name,
                latestRunId = runId,
                occurredAt = document.occurredAt,
                updatedAt = now,
            )
        }
        OrcaLog.error(
            "Marked extraction run=${runId.takeLast(8)} as FAILED with errorCode=$errorCode.",
        )
    }

    suspend fun finishRun(
        runId: String,
        status: String,
        occurredAt: String?,
    ) {
        val run = requireNotNull(dao.getRun(runId))
        val document = requireNotNull(dao.getDocument(run.documentId))
        val now = Instant.now().toString()
        database.withTransaction {
            dao.updateExtractionRun(
                runId = runId,
                status = status,
                errorCode = null,
                startedAt = null,
                completedAt = now,
            )
            dao.updateDocumentExtractionResult(
                documentId = run.documentId,
                title = document.title,
                extractionStatus = status,
                latestRunId = runId,
                occurredAt = occurredAt,
                updatedAt = now,
            )
        }
    }

    suspend fun applyExtractionResult(
        documentId: String,
        runId: String,
        parsed: ParsedExtractionPayload,
        result: NormalizedExtractionResult,
    ) {
        val run = requireNotNull(dao.getRun(runId))
        val document = requireNotNull(dao.getDocument(documentId))
        val now = Instant.now().toString()
        database.withTransaction {
            dao.updateExtractionRunDebugArtifacts(
                runId = runId,
                requestPrompt = parsed.provider.promptText,
                responseEnvelopeJson = parsed.provider.responseEnvelopeJson,
                responseOutputJson = parsed.provider.responseOutputJson,
                normalizationDebugJson = json.encodeToString(result.debugReport),
            )
            dao.updateExtractionRun(
                runId = runId,
                status = ExtractionStatus.SUCCEEDED.name,
                errorCode = null,
                startedAt = run.startedAt,
                completedAt = now,
            )
            dao.deleteFactsByDocument(documentId)
            dao.getEvent(documentId)?.let { existingEvent ->
                dao.deleteServiceItemsByEvent(existingEvent.eventId)
            }
            dao.deleteEventByDocument(documentId)
            if (result.facts.isNotEmpty()) {
                dao.insertFacts(result.facts)
            }
            result.event?.let { dao.insertEvent(it) }
            if (result.serviceItems.isNotEmpty()) {
                dao.insertServiceItems(result.serviceItems)
            }
            dao.updateDocumentExtractionResult(
                documentId = documentId,
                title = result.title ?: document.title,
                extractionStatus = ExtractionStatus.SUCCEEDED.name,
                latestRunId = runId,
                occurredAt = result.event?.eventDate,
                updatedAt = now,
            )
        }
        OrcaLog.info(
            "Applied extraction result for document=${documentId.takeLast(8)} with ${result.facts.size} fact(s) and ${result.serviceItems.size} service item(s).",
        )
    }
}

data class DocumentBundle(
    val document: DocumentEntity,
    val sources: List<DocumentSourceEntity>,
)

data class DocumentDetail(
    val document: DocumentEntity,
    val sources: List<DocumentSourceEntity>,
    val latestRun: ExtractionRunEntity?,
    val facts: List<DocumentFactEntity>,
    val event: MaintenanceEventEntity?,
    val serviceItems: List<ServiceItemEntity>,
)
