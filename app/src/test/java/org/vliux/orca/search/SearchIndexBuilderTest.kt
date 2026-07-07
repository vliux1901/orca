package org.vliux.orca.search

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.data.model.MaintenanceEventEntity
import org.vliux.orca.data.model.ServiceItemEntity

class SearchIndexBuilderTest {
    private val builder = SearchIndexBuilder()

    @Test
    fun buildProducesDeterministicEntriesFromFactsEventAndServiceItems() {
        val document = DocumentEntity(
            documentId = "document-123",
            vehicleId = "vehicle-123",
            title = "BMW 120K service",
            sourceCount = 1,
            importStatus = "READY",
            extractionStatus = "SUCCEEDED",
            latestRunId = "run-1",
            occurredOn = LocalDate.parse("2026-05-01"),
            createdAt = Instant.parse("2026-07-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-07-01T00:00:00Z"),
        )
        val facts = listOf(
            fact(
                factId = "fact-1",
                factType = "LINE_ITEM",
                valueText = "120K3 service",
                sourceId = "source-001",
                sourcePage = 1,
                evidenceText = "120K3 service",
            ),
            fact(
                factId = "fact-2",
                factType = "INTERPRETED_LINE_ITEM",
                valueText = "Likely 120,000-mile scheduled service package",
                sourceId = "source-001",
                sourcePage = 1,
                evidenceText = "120K3 service",
            ),
        )
        val event = MaintenanceEventEntity(
            eventId = "event-1",
            vehicleId = "vehicle-123",
            documentId = "document-123",
            eventDate = LocalDate.parse("2026-05-01"),
            mileage = 32100,
            shopName = "BMW Seattle",
            totalCostMinor = 25999,
            currencyCode = "USD",
            status = "EXTRACTED_UNVERIFIED",
            createdAt = Instant.parse("2026-07-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-07-01T00:00:00Z"),
        )
        val items = listOf(
            ServiceItemEntity(
                serviceItemId = "item-1",
                eventId = "event-1",
                category = "INSPECTION",
                label = "120K package inspection",
                notes = "Includes standard checks",
                costMinor = 12000,
                currencyCode = "USD",
                sourceId = "source-001",
                sourcePage = 1,
                createdAt = Instant.parse("2026-07-01T00:00:00Z"),
            ),
        )

        val first = builder.build(
            document = document,
            title = document.title,
            facts = facts,
            event = event,
            serviceItems = items,
            createdAt = Instant.parse("2026-07-02T00:00:00Z"),
        )
        val second = builder.build(
            document = document,
            title = document.title,
            facts = facts,
            event = event,
            serviceItems = items,
            createdAt = Instant.parse("2026-07-02T00:00:00Z"),
        )

        assertEquals(first, second)
        assertEquals(listOf("EVENT", "SERVICE_ITEM", "FACT", "FACT"), first.map { it.entryType })
        assertEquals("BMW 120K service", first.first().title)
        assertTrue(first.first().body.contains("Services 120K package inspection"))
        assertTrue(first.any { it.entryType == "FACT" && it.body.contains("120K3 service") })
        assertTrue(first.any { it.entryType == "FACT" && it.body.contains("Likely 120,000-mile scheduled service package") })
    }

    private fun fact(
        factId: String,
        factType: String,
        valueText: String,
        sourceId: String,
        sourcePage: Int,
        evidenceText: String,
    ) = DocumentFactEntity(
        factId = factId,
        documentId = "document-123",
        runId = "run-1",
        factType = factType,
        valueText = valueText,
        normalizedValue = null,
        interpretedFromFactId = null,
        currencyCode = null,
        sourceId = sourceId,
        sourcePage = sourcePage,
        evidenceText = evidenceText,
        confidence = 0.8,
        isUserEdited = false,
        replacesFactId = null,
        isActive = true,
        createdAt = Instant.parse("2026-07-01T00:00:00Z"),
    )
}
