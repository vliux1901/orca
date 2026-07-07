package org.vliux.orca.worker

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.data.DocumentStore
import org.vliux.orca.data.PreparedSourceImport
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.data.model.DocumentListItem
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.data.model.ExtractionRunEntity
import org.vliux.orca.data.model.ExtractionStatus
import org.vliux.orca.data.model.MaintenanceEventEntity
import org.vliux.orca.data.model.SearchEntryEntity
import org.vliux.orca.data.model.ServiceItemEntity
import org.vliux.orca.importing.AppFileStorage
import org.vliux.orca.provider.ProviderAuthSource
import org.vliux.orca.provider.ProviderRuntimeConfig

class DocumentExtractionSchedulerTest {
    @Test
    fun queueDocumentExtractionMarksDocumentQueuedAndEnqueuesUniqueWork() = runTest {
        val rootDir = createTempDirectory(prefix = "orca-files-").toFile()
        try {
            val store = SchedulerDocumentStore(knownVehicleIds = setOf("vehicle-123"))
            val repository = DocumentRepository(
                store = store,
                fileStorage = AppFileStorage(rootDir) { error("Uri path should not be used in scheduler tests.") },
            )
            val documentId = repository.createDocument(
                vehicleId = "vehicle-123",
                imports = listOf(
                    preparedImport("receipt.png", "image/png", byteArrayOf(1, 2, 3), rootDir),
                ),
            )
            val enqueuer = RecordingDocumentExtractionWorkEnqueuer()
            val scheduler = DocumentExtractionScheduler(
                repository = repository,
                providerRuntimeConfig = configuredProviderRuntimeConfig(),
                workEnqueuer = enqueuer,
            )

            val queued = scheduler.queueDocumentExtraction(documentId)

            assertTrue(queued)
            assertEquals(listOf(documentId), enqueuer.enqueuedDocumentIds)
            assertEquals(
                ExtractionStatus.QUEUED.name,
                repository.observeDocumentList("vehicle-123").first().single().extractionStatus,
            )
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun queueDocumentExtractionSkipsQueueingWhenProviderIsNotConfigured() = runTest {
        val rootDir = createTempDirectory(prefix = "orca-files-").toFile()
        try {
            val store = SchedulerDocumentStore(knownVehicleIds = setOf("vehicle-123"))
            val repository = DocumentRepository(
                store = store,
                fileStorage = AppFileStorage(rootDir) { error("Uri path should not be used in scheduler tests.") },
            )
            val documentId = repository.createDocument(
                vehicleId = "vehicle-123",
                imports = listOf(
                    preparedImport("receipt.png", "image/png", byteArrayOf(1, 2, 3), rootDir),
                ),
            )
            val enqueuer = RecordingDocumentExtractionWorkEnqueuer()
            val scheduler = DocumentExtractionScheduler(
                repository = repository,
                providerRuntimeConfig = unconfiguredProviderRuntimeConfig(),
                workEnqueuer = enqueuer,
            )

            val queued = scheduler.queueDocumentExtraction(documentId)

            assertFalse(queued)
            assertTrue(enqueuer.enqueuedDocumentIds.isEmpty())
            assertEquals(
                ExtractionStatus.NOT_STARTED.name,
                repository.observeDocumentList("vehicle-123").first().single().extractionStatus,
            )
        } finally {
            rootDir.deleteRecursively()
        }
    }

    private fun preparedImport(
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
        rootDir: File,
    ): PreparedSourceImport = PreparedSourceImport(
        displayName = displayName,
        mimeType = mimeType,
        storeSource = { source ->
            AppFileStorage(rootDir) { error("Uri path should not be used in scheduler tests.") }
                .storeSource(source, mimeType) {
                    bytes.inputStream()
                }
        },
    )

    private fun configuredProviderRuntimeConfig(): ProviderRuntimeConfig = ProviderRuntimeConfig(
        providerName = "openai",
        requestUrl = "https://example.invalid/v1/responses",
        modelName = "gpt-test",
        authToken = "token",
        authSource = ProviderAuthSource.ENVIRONMENT,
    )

    private fun unconfiguredProviderRuntimeConfig(): ProviderRuntimeConfig = ProviderRuntimeConfig(
        providerName = "openai",
        requestUrl = "https://example.invalid/v1/responses",
        modelName = "gpt-test",
        authToken = "",
        authSource = ProviderAuthSource.NONE,
    )
}

private class RecordingDocumentExtractionWorkEnqueuer : DocumentExtractionWorkEnqueuer {
    val enqueuedDocumentIds = mutableListOf<String>()

    override fun enqueue(documentId: String) {
        enqueuedDocumentIds += documentId
    }
}

private class SchedulerDocumentStore(
    private val knownVehicleIds: Set<String>,
) : DocumentStore {
    private val documents = ConcurrentHashMap<String, DocumentEntity>()
    private val sources = ConcurrentHashMap<String, MutableList<DocumentSourceEntity>>()
    private val runs = ConcurrentHashMap<String, ExtractionRunEntity>()
    private val facts = ConcurrentHashMap<String, MutableList<DocumentFactEntity>>()
    private val events = ConcurrentHashMap<String, MaintenanceEventEntity>()
    private val serviceItems = ConcurrentHashMap<String, MutableList<ServiceItemEntity>>()
    private val searchEntries = ConcurrentHashMap<String, MutableList<SearchEntryEntity>>()

    private val documentFlows = ConcurrentHashMap<String, MutableStateFlow<DocumentEntity?>>()
    private val sourceFlows = ConcurrentHashMap<String, MutableStateFlow<List<DocumentSourceEntity>>>()
    private val runFlows = ConcurrentHashMap<String, MutableStateFlow<ExtractionRunEntity?>>()
    private val factFlows = ConcurrentHashMap<String, MutableStateFlow<List<DocumentFactEntity>>>()
    private val eventFlows = ConcurrentHashMap<String, MutableStateFlow<MaintenanceEventEntity?>>()
    private val serviceItemFlows = ConcurrentHashMap<String, MutableStateFlow<List<ServiceItemEntity>>>()
    private val documentListFlows = ConcurrentHashMap<String, MutableStateFlow<List<DocumentListItem>>>()

    override suspend fun <T> inTransaction(block: suspend DocumentStore.() -> T): T = block()

    override suspend fun insertDocument(document: DocumentEntity) {
        check(knownVehicleIds.contains(document.vehicleId)) { "Unknown vehicleId: ${document.vehicleId}" }
        documents[document.documentId] = document
        documentFlow(document.documentId).value = document
        publishDocumentList(document.vehicleId)
    }

    override suspend fun insertSources(sources: List<DocumentSourceEntity>) {
        sources.groupBy { it.documentId }.forEach { (documentId, documentSources) ->
            val existing = this.sources.getOrPut(documentId) { mutableListOf() }
            existing += documentSources
            sourceFlow(documentId).value = existing.sortedBy { it.sourceOrder }
        }
    }

    override suspend fun insertExtractionRun(run: ExtractionRunEntity) {
        runs[run.runId] = run
        runFlow(run.documentId).value = run
    }

    override suspend fun insertFacts(facts: List<DocumentFactEntity>) {
        facts.groupBy { it.documentId }.forEach { (documentId, documentFacts) ->
            val existing = this.facts.getOrPut(documentId) { mutableListOf() }
            existing += documentFacts
            factFlow(documentId).value = existing.filter { it.isActive }.sortedBy { it.createdAt }
        }
    }

    override suspend fun insertEvent(event: MaintenanceEventEntity) {
        events[event.documentId] = event
        eventFlow(event.documentId).value = event
    }

    override suspend fun insertServiceItems(items: List<ServiceItemEntity>) {
        items.groupBy { it.eventId }.forEach { (eventId, newItems) ->
            val existing = serviceItems.getOrPut(eventId) { mutableListOf() }
            existing += newItems
            serviceItemFlow(eventId).value = existing.sortedBy { it.createdAt }
        }
    }

    override suspend fun insertSearchEntries(entries: List<SearchEntryEntity>) {
        entries.groupBy { it.documentId }.forEach { (documentId, newEntries) ->
            val existing = searchEntries.getOrPut(documentId) { mutableListOf() }
            existing += newEntries
        }
    }

    override suspend fun deleteFactsByDocument(documentId: String) {
        facts.remove(documentId)
        factFlow(documentId).value = emptyList()
    }

    override suspend fun deleteEventByDocument(documentId: String) {
        val removed = events.remove(documentId)
        eventFlow(documentId).value = null
        removed?.let {
            serviceItems.remove(it.eventId)
            serviceItemFlow(it.eventId).value = emptyList()
        }
    }

    override suspend fun deleteServiceItemsByEvent(eventId: String) {
        serviceItems.remove(eventId)
        serviceItemFlow(eventId).value = emptyList()
    }

    override suspend fun deleteSearchEntriesByDocument(documentId: String) {
        searchEntries.remove(documentId)
    }

    override suspend fun deleteServiceItemById(serviceItemId: String) {
        serviceItems.forEach { (eventId, items) ->
            if (items.removeAll { it.serviceItemId == serviceItemId }) {
                serviceItemFlow(eventId).value = items.sortedBy { it.createdAt }
            }
        }
    }

    override suspend fun deleteDocument(documentId: String) {
        val removed = documents.remove(documentId) ?: return
        documentFlow(documentId).value = null
        sources.remove(documentId)
        sourceFlow(documentId).value = emptyList()
        facts.remove(documentId)
        factFlow(documentId).value = emptyList()
        searchEntries.remove(documentId)
        val removedEvent = events.remove(documentId)
        eventFlow(documentId).value = null
        removedEvent?.let {
            serviceItems.remove(it.eventId)
            serviceItemFlow(it.eventId).value = emptyList()
        }
        runs.values.removeIf { it.documentId == documentId }
        runFlow(documentId).value = null
        publishDocumentList(removed.vehicleId)
    }

    override suspend fun updateExtractionRun(
        runId: String,
        status: String,
        errorCode: String?,
        startedAt: Instant?,
        completedAt: Instant?,
    ) {
        val run = requireNotNull(runs[runId])
        val updated = run.copy(
            status = status,
            errorCode = errorCode,
            startedAt = startedAt ?: run.startedAt,
            completedAt = completedAt,
        )
        runs[runId] = updated
        runFlow(updated.documentId).value = updated
    }

    override suspend fun updateDocumentState(
        documentId: String,
        sourceCount: Int,
        importStatus: String,
        extractionStatus: String,
        latestRunId: String?,
        occurredOn: LocalDate?,
        updatedAt: Instant,
    ) {
        val document = requireNotNull(documents[documentId])
        val updated = document.copy(
            sourceCount = sourceCount,
            importStatus = importStatus,
            extractionStatus = extractionStatus,
            latestRunId = latestRunId,
            occurredOn = occurredOn,
            updatedAt = updatedAt,
        )
        documents[documentId] = updated
        documentFlow(documentId).value = updated
        publishDocumentList(updated.vehicleId)
    }

    override suspend fun updateDocumentExtractionResult(
        documentId: String,
        title: String?,
        extractionStatus: String,
        latestRunId: String?,
        occurredOn: LocalDate?,
        updatedAt: Instant,
    ) {
        val document = requireNotNull(documents[documentId])
        val updated = document.copy(
            title = title,
            extractionStatus = extractionStatus,
            latestRunId = latestRunId,
            occurredOn = occurredOn,
            updatedAt = updatedAt,
        )
        documents[documentId] = updated
        documentFlow(documentId).value = updated
        publishDocumentList(updated.vehicleId)
    }

    override suspend fun getDocument(documentId: String): DocumentEntity? = documents[documentId]

    override fun observeDocument(documentId: String): Flow<DocumentEntity?> = documentFlow(documentId)

    override suspend fun getSources(documentId: String): List<DocumentSourceEntity> =
        sources[documentId].orEmpty().sortedBy { it.sourceOrder }

    override fun observeSources(documentId: String): Flow<List<DocumentSourceEntity>> = sourceFlow(documentId)

    override suspend fun getLatestRun(documentId: String): ExtractionRunEntity? = runFlow(documentId).value

    override fun observeLatestRun(documentId: String): Flow<ExtractionRunEntity?> = runFlow(documentId)

    override suspend fun getRun(runId: String): ExtractionRunEntity? = runs[runId]

    override suspend fun getEvent(documentId: String): MaintenanceEventEntity? = events[documentId]

    override suspend fun getFact(factId: String): DocumentFactEntity? =
        facts.values.flatMap { it }.firstOrNull { it.factId == factId }

    override suspend fun getActiveFacts(documentId: String): List<DocumentFactEntity> =
        facts[documentId].orEmpty().filter { it.isActive }.sortedBy { it.createdAt }

    override suspend fun deactivateFact(factId: String) {
        facts.forEach { (documentId, items) ->
            val index = items.indexOfFirst { it.factId == factId }
            if (index >= 0) {
                items[index] = items[index].copy(isActive = false)
                factFlow(documentId).value = items.filter { it.isActive }.sortedBy { it.createdAt }
            }
        }
    }

    override suspend fun deactivateActiveFactsByType(documentId: String, factType: String) {
        val items = facts[documentId] ?: return
        var changed = false
        items.indices.forEach { index ->
            val fact = items[index]
            if (fact.factType == factType && fact.isActive) {
                items[index] = fact.copy(isActive = false)
                changed = true
            }
        }
        if (changed) {
            factFlow(documentId).value = items.filter { it.isActive }.sortedBy { it.createdAt }
        }
    }

    override suspend fun getServiceItems(eventId: String): List<ServiceItemEntity> =
        serviceItems[eventId].orEmpty().sortedBy { it.createdAt }

    override suspend fun getServiceItem(serviceItemId: String): ServiceItemEntity? =
        serviceItems.values.flatMap { it }.firstOrNull { it.serviceItemId == serviceItemId }

    override fun observeActiveFacts(documentId: String): Flow<List<DocumentFactEntity>> = factFlow(documentId)

    override fun observeEvent(documentId: String): Flow<MaintenanceEventEntity?> = eventFlow(documentId)

    override fun observeServiceItems(eventId: String): Flow<List<ServiceItemEntity>> = serviceItemFlow(eventId)

    override fun observeDocumentList(vehicleId: String): Flow<List<DocumentListItem>> = documentListFlow(vehicleId)

    private fun publishDocumentList(vehicleId: String) {
        documentListFlow(vehicleId).value = documents.values
            .filter { it.vehicleId == vehicleId }
            .sortedByDescending { it.createdAt }
            .map { document ->
                DocumentListItem(
                    documentId = document.documentId,
                    title = document.title,
                    sourceCount = document.sourceCount,
                    importStatus = document.importStatus,
                    extractionStatus = document.extractionStatus,
                    occurredOn = document.occurredOn,
                    createdAt = document.createdAt,
                    updatedAt = document.updatedAt,
                )
            }
    }

    private fun documentFlow(key: String): MutableStateFlow<DocumentEntity?> =
        documentFlows.getOrPut(key) { MutableStateFlow(null) }

    private fun sourceFlow(key: String): MutableStateFlow<List<DocumentSourceEntity>> =
        sourceFlows.getOrPut(key) { MutableStateFlow(emptyList()) }

    private fun runFlow(key: String): MutableStateFlow<ExtractionRunEntity?> =
        runFlows.getOrPut(key) { MutableStateFlow(null) }

    private fun factFlow(key: String): MutableStateFlow<List<DocumentFactEntity>> =
        factFlows.getOrPut(key) { MutableStateFlow(emptyList()) }

    private fun eventFlow(key: String): MutableStateFlow<MaintenanceEventEntity?> =
        eventFlows.getOrPut(key) { MutableStateFlow(null) }

    private fun serviceItemFlow(key: String): MutableStateFlow<List<ServiceItemEntity>> =
        serviceItemFlows.getOrPut(key) { MutableStateFlow(emptyList()) }

    private fun documentListFlow(key: String): MutableStateFlow<List<DocumentListItem>> =
        documentListFlows.getOrPut(key) { MutableStateFlow(emptyList()) }
}
