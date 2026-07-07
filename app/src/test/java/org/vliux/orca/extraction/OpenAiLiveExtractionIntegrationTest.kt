package org.vliux.orca.extraction

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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
import org.vliux.orca.normalization.ExtractionNormalizer
import org.vliux.orca.provider.ProviderRuntimeConfigResolver
import org.vliux.orca.worker.DocumentExtractionProcessor

class OpenAiLiveExtractionIntegrationTest {
    @Test
    fun processSamplePdfEndToEndWhenLiveProviderIsEnabled() = runTest {
        assumeTrue(
            "Set ORCA_RUN_LIVE_EXTRACTION_TESTS=true to enable live provider tests.",
            System.getenv("ORCA_RUN_LIVE_EXTRACTION_TESTS").equals("true", ignoreCase = true),
        )
        val providerConfig = ProviderRuntimeConfigResolver.fromBuildConfig()
        assumeTrue(
            "Configure ORCA_CLOUD_EXTRACTION_API_KEY or local.properties to run live extraction tests.",
            providerConfig.isConfigured(),
        )

        val rootDir = createTempDirectory(prefix = "orca-live-extraction-").toFile()
        try {
            val repository = DocumentRepository(
                store = LiveIntegrationDocumentStore(knownVehicleIds = setOf("vehicle-123")),
                fileStorage = AppFileStorage(rootDir) { error("Uri path should not be used in live integration tests.") },
            )
            val fixtureBytes = requireNotNull(
                javaClass.classLoader?.getResourceAsStream("fixtures/maintenance-sample.pdf"),
            ) { "Missing fixture fixtures/maintenance-sample.pdf" }.use { it.readBytes() }
            val documentId = repository.createDocument(
                vehicleId = "vehicle-123",
                imports = listOf(
                    preparedImport(
                        displayName = "maintenance-sample.pdf",
                        mimeType = "application/pdf",
                        bytes = fixtureBytes,
                        rootDir = rootDir,
                    ),
                ),
            )
            val processor = DocumentExtractionProcessor(
                repository = repository,
                providerRuntimeConfig = providerConfig,
                extractionEngine = DefaultMaintenanceExtractionEngine(
                    adapter = OpenAiCloudExtractionAdapter(providerConfig.toCloudExtractionConfig()),
                    parser = StrictJsonExtractionParser(),
                ),
                extractionNormalizer = ExtractionNormalizer(),
                fileStorage = AppFileStorage(rootDir) { error("Uri path should not be used in live integration tests.") },
            )

            val outcome = processor.process(documentId)
            val detail = requireNotNull(repository.observeDocumentDetail(documentId).first())
            val factTypes = detail.facts.map { it.factType }.toSet()

            assertEquals(DocumentExtractionProcessor.ProcessingOutcome.Success, outcome)
            assertEquals(ExtractionStatus.SUCCEEDED.name, detail.document.extractionStatus)
            assertEquals(ExtractionStatus.SUCCEEDED.name, detail.latestRun?.status)
            assertTrue("Expected at least one extracted fact.", detail.facts.isNotEmpty())
            assertTrue(
                "Expected at least one core fact type in $factTypes",
                factTypes.intersect(setOf("SERVICE_DATE", "TOTAL_COST", "SHOP_NAME", "ODOMETER")).isNotEmpty(),
            )
            detail.event?.eventDate?.let { eventDate ->
                assertTrue(
                    "Expected event date near fixture date, got $eventDate",
                    eventDate == LocalDate.parse("2026-05-01"),
                )
            }
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
            AppFileStorage(rootDir) { error("Uri path should not be used in live integration tests.") }
                .storeSource(source, mimeType) {
                    bytes.inputStream()
                }
        },
    )
}

private class LiveIntegrationDocumentStore(
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
        events[event.documentId]?.takeIf { it.eventId == event.eventId }?.let { existingEvent ->
            serviceItems.remove(existingEvent.eventId)
            serviceItemFlow(existingEvent.eventId).value = emptyList()
        }
        events[event.documentId] = event
        eventFlow(event.documentId).value = event
    }

    override suspend fun updateEvent(event: MaintenanceEventEntity) {
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

    override suspend fun getLatestRun(documentId: String): ExtractionRunEntity? =
        runs.values.filter { it.documentId == documentId }.maxByOrNull { it.createdAt }

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

    private fun documentFlow(documentId: String): MutableStateFlow<DocumentEntity?> =
        documentFlows.getOrPut(documentId) { MutableStateFlow(documents[documentId]) }

    private fun sourceFlow(documentId: String): MutableStateFlow<List<DocumentSourceEntity>> =
        sourceFlows.getOrPut(documentId) { MutableStateFlow(sources[documentId].orEmpty()) }

    private fun runFlow(documentId: String): MutableStateFlow<ExtractionRunEntity?> =
        runFlows.getOrPut(documentId) { MutableStateFlow(runs.values.filter { it.documentId == documentId }.maxByOrNull { it.createdAt }) }

    private fun factFlow(documentId: String): MutableStateFlow<List<DocumentFactEntity>> =
        factFlows.getOrPut(documentId) { MutableStateFlow(facts[documentId].orEmpty().filter { it.isActive }) }

    private fun eventFlow(documentId: String): MutableStateFlow<MaintenanceEventEntity?> =
        eventFlows.getOrPut(documentId) { MutableStateFlow(events[documentId]) }

    private fun serviceItemFlow(eventId: String): MutableStateFlow<List<ServiceItemEntity>> =
        serviceItemFlows.getOrPut(eventId) { MutableStateFlow(serviceItems[eventId].orEmpty()) }

    private fun documentListFlow(vehicleId: String): MutableStateFlow<List<DocumentListItem>> =
        documentListFlows.getOrPut(vehicleId) { MutableStateFlow(emptyList()) }
}
