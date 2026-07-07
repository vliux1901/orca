package org.vliux.orca.normalization

import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.extraction.AiExtractedFact
import org.vliux.orca.extraction.AiExtractionResponse
import org.vliux.orca.extraction.AiMaintenanceEvent
import org.vliux.orca.extraction.AiServiceItem
import org.vliux.orca.data.model.DocumentSourceEntity

class ExtractionNormalizerTest {
    private var nextId = 0
    private val normalizer = ExtractionNormalizer(
        nowProvider = { Instant.parse("2026-06-28T12:00:00Z") },
        idProvider = { "id-${nextId++}" },
    )

    @Test
    fun normalizeBuildsFactsEventAndItemsFromValidPayload() {
        val result = normalizer.normalize(
            documentId = "doc-1",
            vehicleId = "vehicle-1",
            runId = "run-1",
            response = AiExtractionResponse(
                title = "Brake service",
                facts = listOf(
                    AiExtractedFact(
                        factType = "service_date",
                        valueText = "2026-05-01",
                        normalizedValue = "2026-05-01",
                        sourceId = "src-1",
                        sourcePage = 1,
                        evidenceText = "Service Date 2026-05-01",
                        confidence = 0.99,
                    ),
                    AiExtractedFact(
                        factType = "odometer",
                        valueText = "32,100",
                        normalizedValue = "32100",
                    ),
                ),
                maintenanceEvent = AiMaintenanceEvent(
                    eventDate = "2026-05-01",
                    mileage = 32100,
                    shopName = "  town   tire llc ",
                    totalCostMinor = 25999,
                    currencyCode = "usd",
                ),
                serviceItems = listOf(
                    AiServiceItem(
                        category = "brake svc",
                        label = "Front pads",
                        costMinor = 12000,
                        currencyCode = "usd",
                        sourceId = "src-1",
                        sourcePage = 1,
                    ),
                ),
            ),
            sources = listOf(source("src-1")),
        )

        assertEquals("Brake service", result.title)
        assertEquals(2, result.facts.size)
        assertEquals("SERVICE_DATE", result.facts.first().factType)
        assertNull(result.facts.first().interpretedFromFactId)
        assertEquals(LocalDate.parse("2026-05-01"), result.event?.eventDate)
        assertEquals(32100, result.event?.mileage)
        assertEquals("Town Tire LLC", result.event?.shopName)
        assertEquals("USD", result.event?.currencyCode)
        assertEquals(Instant.parse("2026-06-28T12:00:00Z"), result.facts.first().createdAt)
        assertEquals(Instant.parse("2026-06-28T12:00:00Z"), result.event?.createdAt)
        assertEquals(1, result.serviceItems.size)
        assertEquals("BRAKES", result.serviceItems.first().category)
        assertEquals(Instant.parse("2026-06-28T12:00:00Z"), result.serviceItems.first().createdAt)
        assertEquals("documents.title", result.debugReport.titleDecision.savedTo)
        assertEquals("document_facts", result.debugReport.factDecisions.first().savedTo)
        assertEquals("maintenance_events", result.debugReport.eventDecision.savedTo)
        assertEquals("service_items", result.debugReport.serviceItemDecisions.first().savedTo)
        assertTrue(
            Json.encodeToString(result.debugReport).contains("\"createdAt\":\"2026-06-28T12:00:00Z\""),
        )
        assertTrue(
            Json.encodeToString(result.debugReport).contains("\"eventDate\":\"2026-05-01\""),
        )
    }

    @Test
    fun normalizeDropsInvalidDateAndUnknownSourceIds() {
        val result = normalizer.normalize(
            documentId = "doc-1",
            vehicleId = "vehicle-1",
            runId = "run-1",
            response = AiExtractionResponse(
                facts = listOf(
                    AiExtractedFact(
                        factType = "service_date",
                        valueText = "May sometime",
                        normalizedValue = "May sometime",
                        sourceId = "missing-source",
                    ),
                ),
                maintenanceEvent = AiMaintenanceEvent(
                    eventDate = "bad-date",
                ),
            ),
            sources = listOf(source("src-1")),
        )

        assertNull(result.facts.single().normalizedValue)
        assertNull(result.facts.single().sourceId)
        assertNull(result.event)
        org.junit.Assert.assertTrue(
            result.debugReport.factDecisions.single().reason.contains("normalized_value was rejected because it failed service_date validation."),
        )
        assertEquals(
            "No usable event fields survived normalization.",
            result.debugReport.eventDecision.reason,
        )
    }

    @Test
    fun normalizePersistsInterpretedLineItemWithLinkToRawFact() {
        val result = normalizer.normalize(
            documentId = "doc-1",
            vehicleId = "vehicle-1",
            runId = "run-1",
            response = AiExtractionResponse(
                facts = listOf(
                    AiExtractedFact(
                        factType = "line_item",
                        valueText = "120K3 service",
                        sourceId = "src-1",
                        sourcePage = 1,
                        evidenceText = "120K3 service",
                        confidence = 0.82,
                    ),
                    AiExtractedFact(
                        factType = "interpreted_line_item",
                        valueText = "Likely 120,000-mile scheduled service package",
                        interpretedFromFactType = "line_item",
                        interpretedFromValueText = "120K3 service",
                        sourceId = "src-1",
                        sourcePage = 1,
                        evidenceText = "120K3 service",
                        confidence = 0.64,
                    ),
                ),
            ),
            sources = listOf(source("src-1")),
        )

        assertEquals(2, result.facts.size)
        assertEquals("LINE_ITEM", result.facts[0].factType)
        assertEquals("INTERPRETED_LINE_ITEM", result.facts[1].factType)
        assertEquals(result.facts[0].factId, result.facts[1].interpretedFromFactId)
    }

    @Test
    fun normalizeShopNameFactsAndMapServiceCategoriesToCanonicalValues() {
        val result = normalizer.normalize(
            documentId = "doc-1",
            vehicleId = "vehicle-1",
            runId = "run-1",
            response = AiExtractionResponse(
                facts = listOf(
                    AiExtractedFact(
                        factType = "shop_name",
                        valueText = "  bmw   seattle  llc ",
                    ),
                ),
                serviceItems = listOf(
                    AiServiceItem(
                        category = "maintenance package",
                        label = "120K package inspection",
                        notes = "multipoint inspection and filters",
                    ),
                    AiServiceItem(
                        category = "tire rotation",
                        label = "Rotate all four tires",
                    ),
                ),
                maintenanceEvent = AiMaintenanceEvent(
                    eventDate = "2026-05-01",
                    shopName = "",
                ),
            ),
            sources = listOf(source("src-1")),
        )

        assertEquals("BMW Seattle LLC", result.facts.single().normalizedValue)
        assertEquals("BMW Seattle LLC", result.event?.shopName)
        assertEquals(listOf("INSPECTION", "TIRES"), result.serviceItems.map { it.category })
    }

    private fun source(sourceId: String) = DocumentSourceEntity(
        sourceId = sourceId,
        documentId = "doc-1",
        sourceOrder = 0,
        fileName = "receipt.jpg",
        mimeType = "image/jpeg",
        relativePath = "documents/vehicle-1/doc-1/sources/source-001.jpg",
        byteSize = 100,
        checksumSha256 = null,
        pageCount = null,
        createdAt = Instant.parse("2026-06-28T12:00:00Z"),
    )
}
