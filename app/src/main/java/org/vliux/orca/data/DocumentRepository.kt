package org.vliux.orca.data

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import org.vliux.orca.logging.OrcaLog
import org.vliux.orca.importing.AppFileStorage
import org.vliux.orca.importing.DocumentImportPlanner
import org.vliux.orca.importing.SourceImportPlanInput
import org.vliux.orca.importing.SourceImportOrdering
import org.vliux.orca.importing.SourceImportRequest
import org.vliux.orca.importing.StoredSource
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentImportStatus
import org.vliux.orca.data.model.DocumentListItem
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.data.model.ExtractionRunEntity
import org.vliux.orca.data.model.ExtractionStatus
import org.vliux.orca.data.model.MaintenanceEventEntity
import org.vliux.orca.data.model.SearchEntryEntity
import org.vliux.orca.data.model.ServiceItemEntity
import org.vliux.orca.normalization.NormalizedExtractionResult
import org.vliux.orca.search.SearchIndexBuilder

class DocumentRepository(
    private val store: DocumentStore,
    private val fileStorage: AppFileStorage,
    private val planner: DocumentImportPlanner = DocumentImportPlanner(),
    private val searchIndexBuilder: SearchIndexBuilder = SearchIndexBuilder(),
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val documentListFlows = ConcurrentHashMap<String, SharedFlow<List<DocumentListItem>>>()
    private val documentDetailFlows = ConcurrentHashMap<String, SharedFlow<DocumentDetail?>>()

    suspend fun createDocument(
        vehicleId: String,
        imports: List<SourceImportRequest>,
    ): String = createDocument(
        vehicleId = vehicleId,
        imports = SourceImportOrdering.order(imports).map { request ->
            PreparedSourceImport(
                displayName = request.displayName,
                mimeType = request.mimeType,
                storeSource = { source ->
                    fileStorage.copySource(source, request)
                },
            )
        },
    )

    internal suspend fun createDocument(
        vehicleId: String,
        imports: List<PreparedSourceImport>,
    ): String {
        require(imports.isNotEmpty()) { "At least one import is required." }
        val plan = planner.plan(
            vehicleId = vehicleId,
            imports = imports.map { SourceImportPlanInput(it.displayName, it.mimeType) },
        )

        val storedSources = mutableListOf<StoredSource>()
        try {
            imports.zip(plan.sources).forEach { (request, sourcePlan) ->
                storedSources += request.storeSource(sourcePlan.source)
            }

            store.inTransaction {
                insertDocument(plan.document)
                insertSources(storedSources.map { it.source })
                updateDocumentState(
                    documentId = plan.document.documentId,
                    sourceCount = storedSources.size,
                    importStatus = DocumentImportStatus.READY.name,
                    extractionStatus = ExtractionStatus.NOT_STARTED.name,
                    latestRunId = null,
                    occurredOn = null,
                    updatedAt = plan.document.updatedAt,
                )
            }
        } catch (t: Throwable) {
            fileStorage.deleteRelativePaths(storedSources.map { it.source.relativePath })
            throw t
        }

        OrcaLog.info(
            "Stored document=${plan.document.documentId.takeLast(8)} locally with ${storedSources.size} ordered source file(s).",
        )

        return plan.document.documentId
    }

    suspend fun importDocument(
        vehicleId: String,
        imports: List<SourceImportRequest>,
    ): String = createDocument(vehicleId, imports)

    suspend fun deleteDocument(documentId: String) {
        val document = store.getDocument(documentId) ?: return
        val sourcePaths = store.getSources(documentId).map { it.relativePath }
        store.inTransaction {
            deleteDocument(documentId)
        }
        fileStorage.deleteRelativePaths(sourcePaths)
        OrcaLog.info("Deleted document=${document.documentId.takeLast(8)} and ${sourcePaths.size} stored source file(s).")
    }

    suspend fun deleteDocumentsForVehicle(vehicleId: String) {
        store.getDocumentList(vehicleId).forEach { document ->
            deleteDocument(document.documentId)
        }
    }

    fun observeDocumentList(vehicleId: String): Flow<List<DocumentListItem>> =
        documentListFlows.getOrPut(vehicleId) {
            store.observeDocumentList(vehicleId).shareIn(
                scope = repositoryScope,
                started = SharingStarted.Eagerly,
                replay = 1,
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDocumentDetail(documentId: String): Flow<DocumentDetail?> {
        val eventFlow = store.observeEvent(documentId)
        val documentBundleFlow = combine(
            store.observeDocument(documentId),
            store.observeSources(documentId),
            store.observeLatestRun(documentId),
        ) { document, sources, latestRun ->
            Triple(document, sources, latestRun)
        }
        val extractionFlow = combine(
            store.observeActiveFacts(documentId),
            eventFlow,
            eventFlow.flatMapLatest { event ->
                event?.let { store.observeServiceItems(it.eventId) } ?: flowOf(emptyList())
            },
        ) { facts, event, serviceItems ->
            Triple(facts, event, serviceItems)
        }
        return documentDetailFlows.getOrPut(documentId) {
            combine(documentBundleFlow, extractionFlow) { documentBundle, extractionBundle ->
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
            }.shareIn(
                scope = repositoryScope,
                started = SharingStarted.Eagerly,
                replay = 1,
            )
        }
    }

    suspend fun getDocument(documentId: String): DocumentEntity? =
        store.getDocument(documentId)

    suspend fun getSources(documentId: String): List<DocumentSourceEntity> =
        store.getSources(documentId)

    suspend fun getDocumentBundle(documentId: String): DocumentBundle? {
        val document = store.getDocument(documentId) ?: return null
        return DocumentBundle(
            document = document,
            sources = store.getSources(documentId),
        )
    }

    suspend fun replaceSearchEntries(
        documentId: String,
        entries: List<SearchEntryEntity>,
    ) {
        store.inTransaction {
            deleteSearchEntriesByDocument(documentId)
            if (entries.isNotEmpty()) {
                insertSearchEntries(entries)
            }
        }
    }

    suspend fun markDocumentQueued(documentId: String) {
        val document = requireNotNull(store.getDocument(documentId))
        val now = Instant.now()
        store.updateDocumentState(
            documentId = documentId,
            sourceCount = document.sourceCount,
            importStatus = document.importStatus,
            extractionStatus = ExtractionStatus.QUEUED.name,
            latestRunId = document.latestRunId,
            occurredOn = document.occurredOn,
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
        val document = requireNotNull(store.getDocument(documentId))
        val now = Instant.now()
        val run = ExtractionRunEntity(
            runId = UUID.randomUUID().toString(),
            documentId = documentId,
            backendType = "CLOUD_VISION",
            providerName = providerName,
            modelName = modelName,
            promptVersion = promptVersion,
            status = ExtractionStatus.QUEUED.name,
            errorCode = null,
            startedAt = null,
            completedAt = null,
            createdAt = now,
        )

        store.inTransaction {
            insertExtractionRun(run)
            updateDocumentState(
                documentId = documentId,
                sourceCount = document.sourceCount,
                importStatus = document.importStatus,
                extractionStatus = ExtractionStatus.QUEUED.name,
                latestRunId = run.runId,
                occurredOn = document.occurredOn,
                updatedAt = now,
            )
        }
        OrcaLog.info(
            "Created extraction run=${run.runId.takeLast(8)} for document=${documentId.takeLast(8)} model=$modelName.",
        )
        return run
    }

    suspend fun markRunRunning(runId: String) {
        val run = requireNotNull(store.getRun(runId))
        val document = requireNotNull(store.getDocument(run.documentId))
        val now = Instant.now()
        store.inTransaction {
            updateExtractionRun(
                runId = runId,
                status = ExtractionStatus.RUNNING.name,
                errorCode = null,
                startedAt = now,
                completedAt = null,
            )
            updateDocumentState(
                documentId = run.documentId,
                sourceCount = document.sourceCount,
                importStatus = document.importStatus,
                extractionStatus = ExtractionStatus.RUNNING.name,
                latestRunId = runId,
                occurredOn = document.occurredOn,
                updatedAt = now,
            )
        }
        OrcaLog.info("Marked extraction run=${runId.takeLast(8)} as RUNNING.")
    }

    suspend fun markRunFailed(
        runId: String,
        errorCode: String,
    ) {
        val run = requireNotNull(store.getRun(runId))
        val document = requireNotNull(store.getDocument(run.documentId))
        val now = Instant.now()
        store.inTransaction {
            updateExtractionRun(
                runId = runId,
                status = ExtractionStatus.FAILED.name,
                errorCode = errorCode,
                startedAt = null,
                completedAt = now,
            )
            updateDocumentState(
                documentId = run.documentId,
                sourceCount = document.sourceCount,
                importStatus = document.importStatus,
                extractionStatus = ExtractionStatus.FAILED.name,
                latestRunId = runId,
                occurredOn = document.occurredOn,
                updatedAt = now,
            )
        }
        OrcaLog.error(
            "Marked extraction run=${runId.takeLast(8)} as FAILED with errorCode=$errorCode.",
        )
    }

    suspend fun markRunRetryableFailure(
        runId: String,
        errorCode: String,
    ) {
        val run = requireNotNull(store.getRun(runId))
        val document = requireNotNull(store.getDocument(run.documentId))
        val now = Instant.now()
        store.inTransaction {
            updateExtractionRun(
                runId = runId,
                status = ExtractionStatus.FAILED.name,
                errorCode = errorCode,
                startedAt = run.startedAt,
                completedAt = now,
            )
            updateDocumentState(
                documentId = run.documentId,
                sourceCount = document.sourceCount,
                importStatus = document.importStatus,
                extractionStatus = ExtractionStatus.QUEUED.name,
                latestRunId = runId,
                occurredOn = document.occurredOn,
                updatedAt = now,
            )
        }
        OrcaLog.error(
            "Marked extraction run=${runId.takeLast(8)} as retryable failure with errorCode=$errorCode.",
        )
    }

    suspend fun finishRun(
        runId: String,
        status: String,
        occurredOn: LocalDate?,
    ) {
        val run = requireNotNull(store.getRun(runId))
        val document = requireNotNull(store.getDocument(run.documentId))
        val now = Instant.now()
        store.inTransaction {
            updateExtractionRun(
                runId = runId,
                status = status,
                errorCode = null,
                startedAt = null,
                completedAt = now,
            )
            updateDocumentExtractionResult(
                documentId = run.documentId,
                title = document.title,
                extractionStatus = status,
                latestRunId = runId,
                occurredOn = occurredOn,
                updatedAt = now,
            )
        }
    }

    suspend fun applyExtractionResult(
        documentId: String,
        runId: String,
        result: NormalizedExtractionResult,
    ) {
        val run = requireNotNull(store.getRun(runId))
        val document = requireNotNull(store.getDocument(documentId))
        val now = Instant.now()
        val resolvedTitle = result.title ?: document.title
        val searchEntries = searchIndexBuilder.build(
            document = document,
            title = resolvedTitle,
            facts = result.facts,
            event = result.event,
            serviceItems = result.serviceItems,
            createdAt = now,
        )
        store.inTransaction {
            updateExtractionRun(
                runId = runId,
                status = ExtractionStatus.SUCCEEDED.name,
                errorCode = null,
                startedAt = run.startedAt,
                completedAt = now,
            )
            deleteFactsByDocument(documentId)
            getEvent(documentId)?.let { existingEvent ->
                deleteServiceItemsByEvent(existingEvent.eventId)
            }
            deleteEventByDocument(documentId)
            deleteSearchEntriesByDocument(documentId)
            if (result.facts.isNotEmpty()) {
                insertFacts(result.facts)
            }
            result.event?.let { insertEvent(it) }
            if (result.serviceItems.isNotEmpty()) {
                insertServiceItems(result.serviceItems)
            }
            if (searchEntries.isNotEmpty()) {
                insertSearchEntries(searchEntries)
            }
            updateDocumentExtractionResult(
                documentId = documentId,
                title = resolvedTitle,
                extractionStatus = ExtractionStatus.SUCCEEDED.name,
                latestRunId = runId,
                occurredOn = result.event?.eventDate,
                updatedAt = now,
            )
        }
        OrcaLog.info(
            "Applied extraction result for document=${documentId.takeLast(8)} with ${result.facts.size} fact(s), ${result.serviceItems.size} service item(s), and ${searchEntries.size} search entry/entries.",
        )
    }
}

internal data class PreparedSourceImport(
    val displayName: String?,
    val mimeType: String?,
    val storeSource: (DocumentSourceEntity) -> StoredSource,
)

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
