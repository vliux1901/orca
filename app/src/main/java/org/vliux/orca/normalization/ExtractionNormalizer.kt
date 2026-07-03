package org.vliux.orca.normalization

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlinx.serialization.Serializable
import org.vliux.orca.extraction.AiExtractedFact
import org.vliux.orca.extraction.AiExtractionResponse
import org.vliux.orca.extraction.AiMaintenanceEvent
import org.vliux.orca.extraction.AiServiceItem
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.data.model.MaintenanceEventEntity
import org.vliux.orca.data.model.ServiceItemEntity

class ExtractionNormalizer(
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) {
    fun normalize(
        documentId: String,
        vehicleId: String,
        runId: String,
        response: AiExtractionResponse,
        sources: List<DocumentSourceEntity>,
    ): NormalizedExtractionResult {
        val sourceIds = sources.map { it.sourceId }.toSet()
        val factDecisions = response.facts.map { fact ->
            normalizeFact(
                documentId = documentId,
                runId = runId,
                fact = fact,
                sourceIds = sourceIds,
            )
        }
        val normalizedFacts = factDecisions.mapNotNull { it.savedFact }
        val normalizedEvent = normalizeEvent(
            documentId = documentId,
            vehicleId = vehicleId,
            responseEvent = response.maintenanceEvent,
            facts = normalizedFacts,
        )
        val itemDecisions = response.serviceItems.map { item ->
            normalizeServiceItem(
                eventId = normalizedEvent?.eventId,
                item = item,
                sourceIds = sourceIds,
            )
        }
        val normalizedItems = itemDecisions.mapNotNull { it.savedItem }

        val debugReport = NormalizationDebugReport(
            titleDecision = ValueDecision(
                source = "response.title",
                normalized = response.title?.trim()?.takeIf { it.isNotEmpty() },
                savedTo = "documents.title",
                reason = if (response.title.isNullOrBlank()) {
                    "Blank or missing title was ignored."
                } else {
                    "Trimmed title saved to documents.title."
                },
            ),
            factDecisions = factDecisions,
            eventDecision = EventDecision(
                responseEvent = response.maintenanceEvent,
                savedEvent = normalizedEvent,
                savedTo = normalizedEvent?.let { "maintenance_events" },
                occurredAtSavedTo = normalizedEvent?.eventDate?.let { "documents.occurredAt" },
                reason = if (normalizedEvent == null) {
                    "No usable event fields survived normalization."
                } else {
                    "Normalized event saved to maintenance_events and eventDate mirrored to documents.occurredAt."
                },
            ),
            serviceItemDecisions = itemDecisions,
            warnings = response.warnings,
        )

        return NormalizedExtractionResult(
            title = response.title?.trim()?.takeIf { it.isNotEmpty() },
            facts = normalizedFacts,
            event = normalizedEvent,
            serviceItems = normalizedItems,
            debugReport = debugReport,
        )
    }

    private fun normalizeFact(
        documentId: String,
        runId: String,
        fact: AiExtractedFact,
        sourceIds: Set<String>,
    ): FactDecision {
        val trimmedType = fact.factType.trim().uppercase()
        val trimmedValue = fact.valueText.trim()
        if (trimmedType.isBlank() || trimmedValue.isBlank()) {
            return FactDecision(
                input = fact,
                savedFact = null,
                savedTo = null,
                reason = "Dropped because fact_type or value_text was blank after trimming.",
            )
        }
        val normalizedValue = when (trimmedType) {
            "SERVICE_DATE" -> normalizeDate(fact.normalizedValue ?: trimmedValue)
            "ODOMETER" -> normalizeMileage(fact.normalizedValue ?: trimmedValue)?.toString()
            "TOTAL_COST" -> normalizeCost(fact.normalizedValue ?: trimmedValue)?.toString()
            else -> fact.normalizedValue?.trim()?.takeIf { it.isNotEmpty() }
        }
        val sanitizedSourceId = fact.sourceId?.takeIf(sourceIds::contains)
        val sanitizedSourcePage = fact.sourcePage?.takeIf { it > 0 }
        val sanitizedConfidence = fact.confidence?.takeIf { it in 0.0..1.0 }
        return FactDecision(
            input = fact,
            savedFact = DocumentFactEntity(
            factId = idProvider(),
            documentId = documentId,
            runId = runId,
            factType = trimmedType,
            valueText = trimmedValue,
            normalizedValue = normalizedValue,
            currencyCode = fact.currencyCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
            sourceId = sanitizedSourceId,
            sourcePage = sanitizedSourcePage,
            evidenceText = fact.evidenceText?.trim()?.takeIf { it.isNotEmpty() },
            confidence = sanitizedConfidence,
            isUserEdited = false,
            replacesFactId = null,
            isActive = true,
            createdAt = nowProvider(),
            ),
            savedTo = "document_facts",
            reason = buildString {
                append("Saved to document_facts after trimming type/value.")
                if (fact.sourceId != null && sanitizedSourceId == null) {
                    append(" source_id was dropped because it did not match an imported source.")
                }
                if (fact.sourcePage != null && sanitizedSourcePage == null) {
                    append(" source_page was dropped because it was not > 0.")
                }
                if (fact.confidence != null && sanitizedConfidence == null) {
                    append(" confidence was dropped because it was outside 0.0..1.0.")
                }
                if (fact.normalizedValue != null && normalizedValue == null && trimmedType in setOf("SERVICE_DATE", "ODOMETER", "TOTAL_COST")) {
                    append(" normalized_value was rejected because it failed ${trimmedType.lowercase()} validation.")
                }
            },
        )
    }

    private fun normalizeEvent(
        documentId: String,
        vehicleId: String,
        responseEvent: AiMaintenanceEvent?,
        facts: List<DocumentFactEntity>,
    ): MaintenanceEventEntity? {
        val date = normalizeDate(
            responseEvent?.eventDate ?: facts.firstValue("SERVICE_DATE"),
        )
        val mileage = responseEvent?.mileage?.takeIf { it >= 0 }
            ?: facts.firstValue("ODOMETER")?.let(::normalizeMileage)
        val shopName = responseEvent?.shopName?.trim()?.takeIf { it.isNotEmpty() }
            ?: facts.firstValue("SHOP_NAME")
        val totalCostMinor = responseEvent?.totalCostMinor?.takeIf { it >= 0 }
            ?: facts.firstValue("TOTAL_COST")?.let(::normalizeCost)
        val currencyCode = responseEvent?.currencyCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            ?: facts.firstOrNull { it.factType == "TOTAL_COST" }?.currencyCode

        if (date == null && mileage == null && shopName == null && totalCostMinor == null) {
            return null
        }

        val now = nowProvider()
        return MaintenanceEventEntity(
            eventId = idProvider(),
            vehicleId = vehicleId,
            documentId = documentId,
            eventDate = date,
            mileage = mileage,
            shopName = shopName,
            totalCostMinor = totalCostMinor,
            currencyCode = currencyCode,
            status = "EXTRACTED_UNVERIFIED",
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun normalizeServiceItem(
        eventId: String?,
        item: AiServiceItem,
        sourceIds: Set<String>,
    ): ServiceItemDecision {
        if (eventId == null) {
            return ServiceItemDecision(
                input = item,
                savedItem = null,
                savedTo = null,
                reason = "Dropped because no maintenance event was created for this document.",
            )
        }
        val category = item.category.trim().uppercase()
        val label = item.label.trim()
        if (category.isBlank() || label.isBlank()) {
            return ServiceItemDecision(
                input = item,
                savedItem = null,
                savedTo = null,
                reason = "Dropped because category or label was blank after trimming.",
            )
        }
        val sanitizedSourceId = item.sourceId?.takeIf(sourceIds::contains)
        val sanitizedSourcePage = item.sourcePage?.takeIf { it > 0 }
        return ServiceItemDecision(
            input = item,
            savedItem = ServiceItemEntity(
            serviceItemId = idProvider(),
            eventId = eventId,
            category = category,
            label = label,
            notes = item.notes?.trim()?.takeIf { it.isNotEmpty() },
            costMinor = item.costMinor?.takeIf { it >= 0 },
            currencyCode = item.currencyCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
            sourceId = sanitizedSourceId,
            sourcePage = sanitizedSourcePage,
            createdAt = nowProvider(),
            ),
            savedTo = "service_items",
            reason = buildString {
                append("Saved to service_items for the derived maintenance event.")
                if (item.sourceId != null && sanitizedSourceId == null) {
                    append(" source_id was dropped because it did not match an imported source.")
                }
                if (item.sourcePage != null && sanitizedSourcePage == null) {
                    append(" source_page was dropped because it was not > 0.")
                }
                if (item.costMinor != null && item.costMinor < 0) {
                    append(" cost_minor was dropped because it was negative.")
                }
            },
        )
    }

    private fun normalizeDate(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            LocalDate.parse(value).toString()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun normalizeMileage(raw: String): Int? {
        val digits = raw.filter { it.isDigit() }
        return digits.toIntOrNull()?.takeIf { it in 0..2_000_000 }
    }

    private fun normalizeCost(raw: String): Long? {
        val digits = raw.filter { it.isDigit() }
        return digits.toLongOrNull()?.takeIf { it >= 0 }
    }

    private fun List<DocumentFactEntity>.firstValue(factType: String): String? =
        firstOrNull { it.factType == factType }?.normalizedValue ?: firstOrNull { it.factType == factType }?.valueText
}

data class NormalizedExtractionResult(
    val title: String?,
    val facts: List<DocumentFactEntity>,
    val event: MaintenanceEventEntity?,
    val serviceItems: List<ServiceItemEntity>,
    val debugReport: NormalizationDebugReport,
)

@Serializable
data class NormalizationDebugReport(
    val titleDecision: ValueDecision,
    val factDecisions: List<FactDecision>,
    val eventDecision: EventDecision,
    val serviceItemDecisions: List<ServiceItemDecision>,
    val warnings: List<String>,
)

@Serializable
data class ValueDecision(
    val source: String,
    val normalized: String?,
    val savedTo: String?,
    val reason: String,
)

@Serializable
data class FactDecision(
    val input: AiExtractedFact,
    val savedFact: DocumentFactEntity?,
    val savedTo: String?,
    val reason: String,
)

@Serializable
data class EventDecision(
    val responseEvent: AiMaintenanceEvent?,
    val savedEvent: MaintenanceEventEntity?,
    val savedTo: String?,
    val occurredAtSavedTo: String?,
    val reason: String,
)

@Serializable
data class ServiceItemDecision(
    val input: AiServiceItem,
    val savedItem: ServiceItemEntity?,
    val savedTo: String?,
    val reason: String,
)
