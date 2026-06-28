package org.vliux.orca.prototype.cloudflow.normalization

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import org.vliux.orca.prototype.cloudflow.extraction.AiExtractedFact
import org.vliux.orca.prototype.cloudflow.extraction.AiExtractionResponse
import org.vliux.orca.prototype.cloudflow.extraction.AiMaintenanceEvent
import org.vliux.orca.prototype.cloudflow.extraction.AiServiceItem
import org.vliux.orca.prototype.cloudflow.model.DocumentFactEntity
import org.vliux.orca.prototype.cloudflow.model.DocumentSourceEntity
import org.vliux.orca.prototype.cloudflow.model.MaintenanceEventEntity
import org.vliux.orca.prototype.cloudflow.model.ServiceItemEntity

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
        val normalizedFacts = response.facts.mapNotNull { fact ->
            normalizeFact(
                documentId = documentId,
                runId = runId,
                fact = fact,
                sourceIds = sourceIds,
            )
        }
        val normalizedEvent = normalizeEvent(
            documentId = documentId,
            vehicleId = vehicleId,
            responseEvent = response.maintenanceEvent,
            facts = normalizedFacts,
        )
        val normalizedItems = response.serviceItems.mapNotNull { item ->
            normalizeServiceItem(
                eventId = normalizedEvent?.eventId,
                item = item,
                sourceIds = sourceIds,
            )
        }

        return NormalizedExtractionResult(
            title = response.title?.trim()?.takeIf { it.isNotEmpty() },
            facts = normalizedFacts,
            event = normalizedEvent,
            serviceItems = normalizedItems,
        )
    }

    private fun normalizeFact(
        documentId: String,
        runId: String,
        fact: AiExtractedFact,
        sourceIds: Set<String>,
    ): DocumentFactEntity? {
        val trimmedType = fact.factType.trim().uppercase()
        val trimmedValue = fact.valueText.trim()
        if (trimmedType.isBlank() || trimmedValue.isBlank()) {
            return null
        }
        val normalizedValue = when (trimmedType) {
            "SERVICE_DATE" -> normalizeDate(fact.normalizedValue ?: trimmedValue)
            "ODOMETER" -> normalizeMileage(fact.normalizedValue ?: trimmedValue)?.toString()
            "TOTAL_COST" -> normalizeCost(fact.normalizedValue ?: trimmedValue)?.toString()
            else -> fact.normalizedValue?.trim()?.takeIf { it.isNotEmpty() }
        }
        return DocumentFactEntity(
            factId = idProvider(),
            documentId = documentId,
            runId = runId,
            factType = trimmedType,
            valueText = trimmedValue,
            normalizedValue = normalizedValue,
            currencyCode = fact.currencyCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
            sourceId = fact.sourceId?.takeIf(sourceIds::contains),
            sourcePage = fact.sourcePage?.takeIf { it > 0 },
            evidenceText = fact.evidenceText?.trim()?.takeIf { it.isNotEmpty() },
            confidence = fact.confidence?.takeIf { it in 0.0..1.0 },
            isUserEdited = false,
            replacesFactId = null,
            isActive = true,
            createdAt = nowProvider().toString(),
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

        val now = nowProvider().toString()
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
    ): ServiceItemEntity? {
        if (eventId == null) return null
        val category = item.category.trim().uppercase()
        val label = item.label.trim()
        if (category.isBlank() || label.isBlank()) return null
        return ServiceItemEntity(
            serviceItemId = idProvider(),
            eventId = eventId,
            category = category,
            label = label,
            notes = item.notes?.trim()?.takeIf { it.isNotEmpty() },
            costMinor = item.costMinor?.takeIf { it >= 0 },
            currencyCode = item.currencyCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
            sourceId = item.sourceId?.takeIf(sourceIds::contains),
            sourcePage = item.sourcePage?.takeIf { it > 0 },
            createdAt = nowProvider().toString(),
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
)
