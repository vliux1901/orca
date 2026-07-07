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
        val rawFactsByKey = linkedMapOf<RawFactKey, DocumentFactEntity>()
        val rawFactDecisions = response.facts.filterNot(::isInterpretedFact).map { fact ->
            normalizeFact(
                documentId = documentId,
                runId = runId,
                fact = fact,
                sourceIds = sourceIds,
                rawFactsByKey = rawFactsByKey,
            ).also { decision ->
                decision.savedFact?.takeIf { it.interpretedFromFactId == null }?.let { savedFact ->
                    rawFactsByKey.putIfAbsent(RawFactKey(savedFact.factType, savedFact.valueText), savedFact)
                }
            }
        }
        val interpretedFactDecisions = response.facts.filter(::isInterpretedFact).map { fact ->
            normalizeFact(
                documentId = documentId,
                runId = runId,
                fact = fact,
                sourceIds = sourceIds,
                rawFactsByKey = rawFactsByKey,
            )
        }
        val factDecisions = rawFactDecisions + interpretedFactDecisions
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
                occurredOnSavedTo = normalizedEvent?.eventDate?.let { "documents.occurredOn" },
                reason = if (normalizedEvent == null) {
                    "No usable event fields survived normalization."
                } else {
                    "Normalized event saved to maintenance_events and eventDate mirrored to documents.occurredOn."
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
        rawFactsByKey: Map<RawFactKey, DocumentFactEntity>,
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
        val interpretedFromKey = if (trimmedType == INTERPRETED_LINE_ITEM) {
            RawFactKey(
                factType = fact.interpretedFromFactType?.trim()?.uppercase().orEmpty(),
                valueText = fact.interpretedFromValueText?.trim().orEmpty(),
            ).takeIf { it.factType.isNotEmpty() && it.valueText.isNotEmpty() }
        } else {
            null
        }
        val interpretedFromFact = interpretedFromKey?.let(rawFactsByKey::get)
        if (trimmedType == INTERPRETED_LINE_ITEM && interpretedFromFact == null) {
            return FactDecision(
                input = fact,
                savedFact = null,
                savedTo = null,
                reason = if (interpretedFromKey == null) {
                    "Dropped because INTERPRETED_LINE_ITEM requires interpreted_from_fact_type and interpreted_from_value_text."
                } else {
                    "Dropped because INTERPRETED_LINE_ITEM could not match a previously normalized raw fact."
                },
            )
        }
        if (trimmedType == INTERPRETED_LINE_ITEM && interpretedFromKey?.factType !in setOf("LINE_ITEM", "NOTE")) {
            return FactDecision(
                input = fact,
                savedFact = null,
                savedTo = null,
                reason = "Dropped because INTERPRETED_LINE_ITEM may only interpret a LINE_ITEM or NOTE raw fact.",
            )
        }
        val normalizedValue = when (trimmedType) {
            "SERVICE_DATE" -> normalizeDate(fact.normalizedValue ?: trimmedValue)?.toString()
            "ODOMETER" -> normalizeMileage(fact.normalizedValue ?: trimmedValue)?.toString()
            "TOTAL_COST" -> normalizeCost(fact.normalizedValue ?: trimmedValue)?.toString()
            "SHOP_NAME" -> NormalizationRules.normalizeShopName(fact.normalizedValue ?: trimmedValue)
            else -> fact.normalizedValue?.trim()?.takeIf { it.isNotEmpty() }
        }
        val sanitizedSourceId = (fact.sourceId?.takeIf(sourceIds::contains)
            ?: interpretedFromFact?.sourceId)
        val sanitizedSourcePage = (fact.sourcePage?.takeIf { it > 0 }
            ?: interpretedFromFact?.sourcePage)
        val sanitizedConfidence = fact.confidence?.takeIf { it in 0.0..1.0 }
        val sanitizedEvidenceText = fact.evidenceText?.trim()?.takeIf { it.isNotEmpty() }
            ?: interpretedFromFact?.evidenceText
        val reviewWarning = reviewWarningFor(
            factType = trimmedType,
            valueText = trimmedValue,
            evidenceText = sanitizedEvidenceText,
        )
        if (trimmedType == INTERPRETED_LINE_ITEM && sanitizedEvidenceText == null) {
            return FactDecision(
                input = fact,
                savedFact = null,
                savedTo = null,
                reason = "Dropped because INTERPRETED_LINE_ITEM requires evidence_text for review.",
            )
        }
        if (trimmedType == INTERPRETED_LINE_ITEM && sanitizedConfidence == null) {
            return FactDecision(
                input = fact,
                savedFact = null,
                savedTo = null,
                reason = "Dropped because INTERPRETED_LINE_ITEM requires confidence for review.",
            )
        }
        if (trimmedType == INTERPRETED_LINE_ITEM && sanitizedSourceId == null && sanitizedSourcePage == null) {
            return FactDecision(
                input = fact,
                savedFact = null,
                savedTo = null,
                reason = "Dropped because INTERPRETED_LINE_ITEM requires at least source_id or source_page for citation.",
            )
        }
        return FactDecision(
            input = fact,
            savedFact = DocumentFactEntity(
                factId = idProvider(),
                documentId = documentId,
                runId = runId,
                factType = trimmedType,
                valueText = trimmedValue,
                normalizedValue = normalizedValue,
                interpretedFromFactId = interpretedFromFact?.factId,
                currencyCode = fact.currencyCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
                sourceId = sanitizedSourceId,
                sourcePage = sanitizedSourcePage,
                evidenceText = sanitizedEvidenceText,
                reviewWarning = reviewWarning,
                confidence = sanitizedConfidence,
                isUserEdited = false,
                replacesFactId = null,
                isActive = true,
                createdAt = nowProvider(),
            ),
            savedTo = "document_facts",
            reason = buildString {
                append("Saved to document_facts after trimming type/value.")
                if (interpretedFromFact != null) {
                    append(" Linked interpreted fact to raw factId=${interpretedFromFact.factId}.")
                    if (fact.sourceId == null && sanitizedSourceId != null) {
                        append(" source_id inherited from the raw fact citation.")
                    }
                    if (fact.sourcePage == null && sanitizedSourcePage != null) {
                        append(" source_page inherited from the raw fact citation.")
                    }
                    if (fact.evidenceText.isNullOrBlank() && sanitizedEvidenceText != null) {
                        append(" evidence_text inherited from the raw fact citation.")
                    }
                }
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
                if (reviewWarning != null) {
                    append(" reviewWarning=").append(reviewWarning)
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
        val shopName = NormalizationRules.normalizeShopName(responseEvent?.shopName)
            ?: NormalizationRules.normalizeShopName(facts.firstValue("SHOP_NAME"))
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
        val category = NormalizationRules.normalizeServiceCategory(
            rawCategory = item.category,
            label = item.label,
            notes = item.notes,
        ).orEmpty()
        val label = NormalizationRules.normalizeWhitespace(item.label).orEmpty()
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
            notes = NormalizationRules.normalizeWhitespace(item.notes),
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

    private fun normalizeDate(raw: String?): LocalDate? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            LocalDate.parse(value)
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

    private fun isInterpretedFact(fact: AiExtractedFact): Boolean =
        fact.factType.trim().uppercase() == INTERPRETED_LINE_ITEM

    private fun reviewWarningFor(
        factType: String,
        valueText: String,
        evidenceText: String?,
    ): String? {
        if (factType !in setOf("LINE_ITEM", "NOTE")) return null
        val evidence = evidenceText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (normalizeMatchingText(valueText) == normalizeMatchingText(evidence)) return null

        val evidenceCodeTokens = codeLikeTokens(evidence)
        if (evidenceCodeTokens.isEmpty()) return null

        val valueTokens = normalizeMatchingText(valueText)
            .split(' ')
            .filter { it.isNotBlank() }
            .toSet()
        return if (evidenceCodeTokens.any { it !in valueTokens }) {
            "Extracted text may not match source evidence exactly. Review the original line."
        } else {
            null
        }
    }

    private fun codeLikeTokens(text: String): Set<String> =
        CODE_TOKEN_REGEX.findAll(text)
            .map { normalizeMatchingText(it.value) }
            .filter { token -> token.any(Char::isLetter) && token.any(Char::isDigit) }
            .filter { it.isNotBlank() }
            .toSet()

    private fun normalizeMatchingText(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private data class RawFactKey(
        val factType: String,
        val valueText: String,
    )

    companion object {
        private const val INTERPRETED_LINE_ITEM = "INTERPRETED_LINE_ITEM"
        private val CODE_TOKEN_REGEX = Regex("[A-Za-z0-9-]{3,}")
    }
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
    val occurredOnSavedTo: String?,
    val reason: String,
)

@Serializable
data class ServiceItemDecision(
    val input: AiServiceItem,
    val savedItem: ServiceItemEntity?,
    val savedTo: String?,
    val reason: String,
)
