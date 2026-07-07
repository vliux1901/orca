package org.vliux.orca.ui.vehicle

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.data.DocumentDetail
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.data.model.MaintenanceEventEntity
import org.vliux.orca.data.model.ServiceItemEntity

class DocumentDetailScreenModelsTest {
    @Test
    fun resolveServiceItemDisplayModelsPrefersMatchingLineItemOnSameSourceAndPage() {
        val detail = documentDetail(
            sources = listOf(
                source(
                    sourceId = "source-1",
                    fileName = "receipt.jpg",
                    mimeType = "image/jpeg",
                ),
            ),
            facts = listOf(
                fact(
                    factId = "fact-1",
                    valueText = "Replace engine air filter",
                    sourceId = "source-1",
                    sourcePage = 1,
                ),
                fact(
                    factId = "fact-2",
                    valueText = "Rotate tires",
                    sourceId = "source-1",
                    sourcePage = 1,
                ),
            ),
            serviceItems = listOf(
                serviceItem(
                    serviceItemId = "service-1",
                    label = "Engine air filter",
                    sourceId = "source-1",
                    sourcePage = 1,
                ),
            ),
        )

        val models = resolveServiceItemDisplayModels(detail)

        assertEquals(1, models.size)
        assertEquals("fact-1", models.single().linkedLineItem?.factId)
        assertEquals("source-1", models.single().linkedSource?.sourceId)
    }

    @Test
    fun resolveServiceItemDisplayModelsReturnsNullWhenNoCompatibleLineItemExists() {
        val detail = documentDetail(
            facts = listOf(
                fact(
                    factId = "fact-1",
                    valueText = "Replace engine air filter",
                    sourceId = "source-1",
                    sourcePage = 1,
                ),
            ),
            serviceItems = listOf(
                serviceItem(
                    serviceItemId = "service-1",
                    label = "Brake fluid flush",
                    sourceId = "source-9",
                    sourcePage = 2,
                ),
            ),
        )

        val models = resolveServiceItemDisplayModels(detail)

        assertEquals(1, models.size)
        assertNull(models.single().linkedLineItem)
    }

    @Test
    fun previewableImageSourcesKeepsOnlyImageMimeTypes() {
        val now = Instant.parse("2026-07-06T12:00:00Z")
        val sources = listOf(
            DocumentSourceEntity(
                sourceId = "source-1",
                documentId = "document-1",
                sourceOrder = 0,
                fileName = "receipt.jpg",
                mimeType = "image/jpeg",
                relativePath = "files/receipt.jpg",
                byteSize = 100L,
                checksumSha256 = null,
                pageCount = null,
                createdAt = now,
            ),
            DocumentSourceEntity(
                sourceId = "source-2",
                documentId = "document-1",
                sourceOrder = 1,
                fileName = "invoice.pdf",
                mimeType = "application/pdf",
                relativePath = "files/invoice.pdf",
                byteSize = 100L,
                checksumSha256 = null,
                pageCount = 1,
                createdAt = now,
            ),
        )

        val previewable = previewableImageSources(sources)

        assertEquals(listOf("source-1"), previewable.map { it.sourceId })
    }

    @Test
    fun resolveServiceItemDisplayModelsPreservesLineItemReviewWarning() {
        val warning = "Extracted text may not match source evidence exactly. Review the original line."
        val detail = documentDetail(
            facts = listOf(
                fact(
                    factId = "fact-1",
                    valueText = "Recall: Replace starter battery",
                    sourceId = "source-1",
                    sourcePage = 1,
                ).copy(
                    evidenceText = "Recall: Replace Starter -B4x",
                    reviewWarning = warning,
                ),
            ),
            serviceItems = listOf(
                serviceItem(
                    serviceItemId = "service-1",
                    label = "Starter recall",
                    sourceId = "source-1",
                    sourcePage = 1,
                ),
            ),
        )

        val models = resolveServiceItemDisplayModels(detail)

        assertEquals(warning, models.single().linkedLineItem?.reviewWarning)
    }

    @Test
    fun documentDeleteHelpersProduceExpectedActionAndConfirmationText() {
        assertEquals("Delete", documentDeleteActionLabel(isDeleting = false))
        assertEquals("Deleting...", documentDeleteActionLabel(isDeleting = true))
        assertTrue(documentDeleteConfirmationMessage("Invoice 42").contains("\"Invoice 42\""))
        assertTrue(documentDeleteConfirmationMessage(null).contains("This cannot be undone."))
    }

    private fun documentDetail(
        sources: List<DocumentSourceEntity> = emptyList(),
        facts: List<DocumentFactEntity>,
        serviceItems: List<ServiceItemEntity>,
    ): DocumentDetail {
        val now = Instant.parse("2026-07-06T12:00:00Z")
        return DocumentDetail(
            document = DocumentEntity(
                documentId = "document-1",
                vehicleId = "vehicle-1",
                title = "Receipt",
                sourceCount = 1,
                importStatus = "READY",
                extractionStatus = "SUCCEEDED",
                latestRunId = "run-1",
                occurredOn = LocalDate.parse("2026-05-01"),
                createdAt = now,
                updatedAt = now,
            ),
            sources = sources,
            latestRun = null,
            facts = facts,
            event = MaintenanceEventEntity(
                eventId = "event-1",
                vehicleId = "vehicle-1",
                documentId = "document-1",
                eventDate = LocalDate.parse("2026-05-01"),
                mileage = 120000,
                shopName = "Town Tire",
                totalCostMinor = 25999,
                currencyCode = "USD",
                status = "UNVERIFIED",
                createdAt = now,
                updatedAt = now,
            ),
            serviceItems = serviceItems,
        )
    }

    private fun fact(
        factId: String,
        valueText: String,
        sourceId: String?,
        sourcePage: Int?,
    ) = DocumentFactEntity(
        factId = factId,
        documentId = "document-1",
        runId = "run-1",
        factType = "LINE_ITEM",
        valueText = valueText,
        normalizedValue = null,
        interpretedFromFactId = null,
        currencyCode = null,
        sourceId = sourceId,
        sourcePage = sourcePage,
        evidenceText = valueText,
        reviewWarning = null,
        confidence = 0.9,
        isUserEdited = false,
        replacesFactId = null,
        isActive = true,
        createdAt = Instant.parse("2026-07-06T12:00:00Z"),
    )

    private fun serviceItem(
        serviceItemId: String,
        label: String,
        sourceId: String?,
        sourcePage: Int?,
    ) = ServiceItemEntity(
        serviceItemId = serviceItemId,
        eventId = "event-1",
        category = "maintenance",
        label = label,
        notes = null,
        costMinor = null,
        currencyCode = null,
        sourceId = sourceId,
        sourcePage = sourcePage,
        createdAt = Instant.parse("2026-07-06T12:00:00Z"),
    )

    private fun source(
        sourceId: String,
        fileName: String,
        mimeType: String,
    ) = DocumentSourceEntity(
        sourceId = sourceId,
        documentId = "document-1",
        sourceOrder = 0,
        fileName = fileName,
        mimeType = mimeType,
        relativePath = "files/$fileName",
        byteSize = 100L,
        checksumSha256 = null,
        pageCount = null,
        createdAt = Instant.parse("2026-07-06T12:00:00Z"),
    )
}
