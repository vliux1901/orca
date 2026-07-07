package org.vliux.orca.data

import java.time.Instant
import java.util.UUID
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.normalization.NormalizationRules

data class FactReplacement(
    val factType: String,
    val valueText: String,
    val note: String? = null,
)

class FactRepository(
    private val store: DocumentStore,
    private val derivedStateRebuilder: DocumentDerivedStateRebuilder,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun replaceFact(
        documentId: String,
        replacedFactId: String?,
        replacement: FactReplacement,
    ) {
        val document = requireNotNull(store.getDocument(documentId)) { "Unknown documentId: $documentId" }
        val replacedFact = replacedFactId?.let { factId ->
            requireNotNull(store.getFact(factId)) { "Unknown factId: $factId" }.also { fact ->
                require(fact.documentId == documentId) { "Fact does not belong to documentId=$documentId" }
            }
        }

        val factType = replacement.factType.trim().uppercase()
        require(factType in SUPPORTED_CORRECTION_TYPES) { "Unsupported correction factType: $factType" }
        if (replacedFact != null) {
            require(replacedFact.factType == factType) {
                "Replacement factType $factType does not match replaced fact type ${replacedFact.factType}."
            }
        }

        val rawValue = replacement.valueText.trim()
        require(rawValue.isNotEmpty()) { "Correction value cannot be blank." }

        val normalizedValue = normalizeReplacementValue(factType, rawValue)
            ?: error("Correction value is invalid for $factType.")
        val now = nowProvider()
        val replacementFact = DocumentFactEntity(
            factId = idProvider(),
            documentId = document.documentId,
            runId = null,
            factType = factType,
            valueText = when (factType) {
                "SHOP_NAME" -> normalizedValue
                else -> rawValue
            },
            normalizedValue = normalizedValue,
            interpretedFromFactId = null,
            currencyCode = if (factType == "TOTAL_COST") {
                replacedFact?.currencyCode ?: store.getEvent(documentId)?.currencyCode
            } else {
                null
            },
            sourceId = replacedFact?.sourceId,
            sourcePage = replacedFact?.sourcePage,
            evidenceText = replacedFact?.evidenceText,
            reviewWarning = null,
            userNote = NormalizationRules.normalizeWhitespace(replacement.note),
            confidence = null,
            isUserEdited = true,
            replacesFactId = replacedFact?.factId,
            isActive = true,
            createdAt = now,
        )

        store.inTransaction {
            deactivateActiveFactsByType(documentId, factType)
            insertFacts(listOf(replacementFact))
        }
        derivedStateRebuilder.rebuild(documentId = documentId, markUserVerified = true)
    }

    private fun normalizeReplacementValue(
        factType: String,
        rawValue: String,
    ): String? = when (factType) {
        "SERVICE_DATE" -> NormalizationRules.normalizeDate(rawValue)?.toString()
        "ODOMETER" -> NormalizationRules.normalizeMileage(rawValue)?.toString()
        "SHOP_NAME" -> NormalizationRules.normalizeShopName(rawValue)
        "TOTAL_COST" -> NormalizationRules.normalizeCostMinor(rawValue)?.toString()
        else -> null
    }

    companion object {
        val SUPPORTED_CORRECTION_TYPES = setOf("SERVICE_DATE", "ODOMETER", "SHOP_NAME", "TOTAL_COST")
    }
}
