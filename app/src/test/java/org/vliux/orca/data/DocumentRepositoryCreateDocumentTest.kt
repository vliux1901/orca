package org.vliux.orca.data

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.data.model.DocumentImportStatus
import org.vliux.orca.data.model.DocumentListItem
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.data.model.ExtractionRunEntity
import org.vliux.orca.data.model.ExtractionStatus
import org.vliux.orca.data.model.MaintenanceEventEntity
import org.vliux.orca.data.model.SearchEntryEntity
import org.vliux.orca.data.model.ServiceItemEntity
import org.vliux.orca.importing.AppFileStorage

class DocumentRepositoryCreateDocumentTest {
    @Test
    fun createDocumentCopiesOrderedSourcesAndExposesAggregateState() = runTest {
        val rootDir = createTempDirectory(prefix = "orca-files-").toFile()
        try {
            val store = InMemoryDocumentStore(knownVehicleIds = setOf("vehicle-123"))
            val repository = DocumentRepository(
                store = store,
                fileStorage = AppFileStorage(rootDir) { error("Uri path should not be used in prepared-import tests.") },
            )

            val documentId = repository.createDocument(
                vehicleId = "vehicle-123",
                imports = listOf(
                    preparedImport("receipt.png", "image/png", byteArrayOf(1, 2, 3), rootDir),
                    preparedImport("invoice.pdf", "application/pdf", byteArrayOf(4, 5, 6, 7), rootDir),
                ),
            )

            val listItems = repository.observeDocumentList("vehicle-123").first()
            val detail = repository.observeDocumentDetail(documentId).first()
            val bundle = repository.getDocumentBundle(documentId)

            assertEquals(1, listItems.size)
            assertEquals(documentId, listItems.single().documentId)
            assertEquals(2, listItems.single().sourceCount)
            assertNotNull(detail)
            assertEquals(2, detail?.sources?.size)
            assertEquals(documentId, bundle?.document?.documentId)
            assertEquals(
                listOf(
                    "vehicles/vehicle-123/documents/$documentId/sources/source-001.png",
                    "vehicles/vehicle-123/documents/$documentId/sources/source-002.pdf",
                ),
                detail?.sources?.map { it.relativePath },
            )

            val copiedFiles = detail?.sources.orEmpty().map { source -> File(rootDir, source.relativePath) }
            assertTrue(copiedFiles.all(File::exists))
            assertArrayEquals(byteArrayOf(1, 2, 3), copiedFiles[0].readBytes())
            assertArrayEquals(byteArrayOf(4, 5, 6, 7), copiedFiles[1].readBytes())
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun createDocumentCleansCopiedFilesWhenStoreInsertFails() = runTest {
        val rootDir = createTempDirectory(prefix = "orca-files-").toFile()
        try {
            val store = InMemoryDocumentStore(knownVehicleIds = emptySet())
            val repository = DocumentRepository(
                store = store,
                fileStorage = AppFileStorage(rootDir) { error("Uri path should not be used in prepared-import tests.") },
            )

            var failed = false
            try {
                repository.createDocument(
                    vehicleId = "missing-vehicle",
                    imports = listOf(
                        preparedImport("receipt.png", "image/png", byteArrayOf(1, 2, 3), rootDir),
                        preparedImport("invoice.pdf", "application/pdf", byteArrayOf(4, 5, 6, 7), rootDir),
                    ),
                )
            } catch (_: IllegalStateException) {
                failed = true
            }

            assertTrue(failed)
            assertFalse(File(rootDir, "vehicles/missing-vehicle").exists())
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
            AppFileStorage(rootDir) { error("Uri path should not be used in prepared-import tests.") }
                .storeSource(source, mimeType) {
                    bytes.inputStream()
                }
        },
    )
}

private class InMemoryDocumentStore(
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

    override suspend fun updateExtractionRunDebugArtifacts(
        runId: String,
        requestPrompt: String?,
        responseEnvelopeJson: String?,
        responseOutputJson: String?,
        normalizationDebugJson: String?,
    ) {
        val run = requireNotNull(runs[runId])
        val updated = run.copy(
            requestPrompt = requestPrompt,
            responseEnvelopeJson = responseEnvelopeJson,
            responseOutputJson = responseOutputJson,
            normalizationDebugJson = normalizationDebugJson,
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
    override suspend fun getSources(documentId: String): List<DocumentSourceEntity> = sources[documentId].orEmpty().sortedBy { it.sourceOrder }
    override fun observeSources(documentId: String): Flow<List<DocumentSourceEntity>> = sourceFlow(documentId)
    override suspend fun getLatestRun(documentId: String): ExtractionRunEntity? = runFlow(documentId).value
    override fun observeLatestRun(documentId: String): Flow<ExtractionRunEntity?> = runFlow(documentId)
    override suspend fun getRun(runId: String): ExtractionRunEntity? = runs[runId]
    override suspend fun getEvent(documentId: String): MaintenanceEventEntity? = events[documentId]
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
