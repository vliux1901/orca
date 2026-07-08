package org.vliux.orca.search

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.data.QaEventRecord
import org.vliux.orca.data.QaFactRecord
import org.vliux.orca.data.QaServiceRecord

class SearchAnswerEngineTest {
    private val engine = SearchAnswerEngine()

    @Test
    fun answersLatestServiceQuestionFromNewestEvent() {
        val answer = engine.answer(
            question = "When was the last service?",
            events = listOf(
                event(documentId = "document-1", documentTitle = "Brake Visit", eventDate = LocalDate.parse("2026-06-10")),
                event(documentId = "document-2", documentTitle = "Oil Change", eventDate = LocalDate.parse("2026-07-01")),
            ),
            services = listOf(
                service(eventId = "event-document-2", documentId = "document-2", category = "OIL_CHANGE", label = "Oil and filter changed"),
            ),
            facts = emptyList(),
        )

        assertTrue(answer.isSupported)
        assertTrue(answer.answer.contains("Jul 1, 2026"))
        assertTrue(answer.answer.contains("Oil and filter changed"))
        assertEquals(LocalDate.parse("2026-07-01"), answer.relatedDate)
        assertEquals(1, answer.citations.size)
    }

    @Test
    fun answersComponentQuestionWithConflictNoteWhenLatestDateHasMultipleMatches() {
        val answer = engine.answer(
            question = "What was last done to the brakes?",
            events = listOf(event(documentId = "document-1", documentTitle = "Brake Visit", eventDate = LocalDate.parse("2026-07-01"))),
            services = listOf(
                service(
                    serviceItemId = "service-1",
                    eventId = "event-document-1",
                    documentId = "document-1",
                    category = "BRAKES",
                    label = "Brake fluid replaced",
                    sourcePage = 2,
                ),
                service(
                    serviceItemId = "service-2",
                    eventId = "event-document-1",
                    documentId = "document-1",
                    category = "BRAKES",
                    label = "Brake pad inspection",
                    sourcePage = 3,
                ),
            ),
            facts = listOf(
                fact(documentId = "document-1", factType = "ODOMETER", valueText = "45123"),
            ),
        )

        assertTrue(answer.isSupported)
        assertTrue(answer.answer.contains("Brake fluid replaced"))
        assertTrue(answer.answer.contains("Brake pad inspection"))
        assertEquals("More than one matching maintenance item shares the latest recorded date.", answer.uncertaintyNote)
        assertEquals(45123, answer.relatedMileage)
        assertEquals(2, answer.citations.size)
    }

    @Test
    fun marksUnsupportedQuestionsExplicitly() {
        val answer = engine.answer(
            question = "Should I replace my tires next month?",
            events = emptyList(),
            services = emptyList(),
            facts = emptyList(),
        )

        assertFalse(answer.isSupported)
        assertNotNull(answer.uncertaintyNote)
        assertTrue(answer.answer.contains("supports maintenance-history questions"))
    }

    private fun event(
        documentId: String,
        documentTitle: String,
        eventDate: LocalDate,
    ) = QaEventRecord(
        eventId = "event-$documentId",
        vehicleId = "vehicle-123",
        documentId = documentId,
        documentTitle = documentTitle,
        eventDate = eventDate,
        mileage = 45123,
        shopName = "North Shop",
        totalCostMinor = null,
        currencyCode = null,
        createdAt = Instant.parse("2026-07-08T00:00:00Z"),
    )

    private fun service(
        serviceItemId: String = "service-123",
        eventId: String,
        documentId: String,
        category: String,
        label: String,
        sourcePage: Int? = 1,
    ) = QaServiceRecord(
        serviceItemId = serviceItemId,
        eventId = eventId,
        vehicleId = "vehicle-123",
        documentId = documentId,
        documentTitle = "Maintenance document",
        category = category,
        label = label,
        notes = null,
        costMinor = null,
        currencyCode = null,
        eventDate = LocalDate.parse("2026-07-01"),
        mileage = null,
        shopName = "North Shop",
        sourceId = "source-123",
        sourcePage = sourcePage,
        createdAt = Instant.parse("2026-07-08T00:00:00Z"),
    )

    private fun fact(
        documentId: String,
        factType: String,
        valueText: String,
    ) = QaFactRecord(
        factId = "fact-$documentId-$factType",
        vehicleId = "vehicle-123",
        documentId = documentId,
        documentTitle = "Maintenance document",
        factType = factType,
        valueText = valueText,
        normalizedValue = valueText,
        sourceId = "source-123",
        sourcePage = 1,
        isUserEdited = false,
        createdAt = Instant.parse("2026-07-08T00:00:00Z"),
    )
}
