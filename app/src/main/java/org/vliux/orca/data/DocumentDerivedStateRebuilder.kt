package org.vliux.orca.data

import java.time.Instant
import java.util.UUID
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.data.model.MaintenanceEventEntity
import org.vliux.orca.data.model.ServiceItemEntity
import org.vliux.orca.normalization.NormalizationRules
import org.vliux.orca.search.SearchIndexBuilder

class DocumentDerivedStateRebuilder(
    private val store: DocumentStore,
    private val searchIndexBuilder: SearchIndexBuilder,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun rebuild(
        documentId: String,
        markUserVerified: Boolean = false,
    ) {
        val document = requireNotNull(store.getDocument(documentId)) { "Unknown documentId: $documentId" }
        val existingEvent = store.getEvent(documentId)
        val activeFacts = store.getActiveFacts(documentId)
        val serviceItems = existingEvent?.let { store.getServiceItems(it.eventId) }.orEmpty()
        val now = nowProvider()
        val rebuiltEvent = deriveEvent(
            document = document,
            existingEvent = existingEvent,
            facts = activeFacts,
            serviceItems = serviceItems,
            now = now,
            markUserVerified = markUserVerified,
        )
        val searchEntries = searchIndexBuilder.build(
            document = document,
            title = document.title,
            facts = activeFacts,
            event = rebuiltEvent,
            serviceItems = serviceItems,
            createdAt = now,
        )

        store.inTransaction {
            when {
                rebuiltEvent == null && existingEvent != null -> deleteEventByDocument(documentId)
                rebuiltEvent != null && existingEvent != null -> updateEvent(rebuiltEvent)
                rebuiltEvent != null -> insertEvent(rebuiltEvent)
            }
            deleteSearchEntriesByDocument(documentId)
            if (searchEntries.isNotEmpty()) {
                insertSearchEntries(searchEntries)
            }
            updateDocumentExtractionResult(
                documentId = document.documentId,
                title = document.title,
                extractionStatus = document.extractionStatus,
                latestRunId = document.latestRunId,
                occurredOn = rebuiltEvent?.eventDate,
                updatedAt = now,
            )
        }
    }

    private fun deriveEvent(
        document: DocumentEntity,
        existingEvent: MaintenanceEventEntity?,
        facts: List<DocumentFactEntity>,
        serviceItems: List<ServiceItemEntity>,
        now: Instant,
        markUserVerified: Boolean,
    ): MaintenanceEventEntity? {
        val date = facts.firstNormalizedOrValue("SERVICE_DATE")?.let(NormalizationRules::normalizeDate)
            ?: existingEvent?.eventDate
            ?: document.occurredOn
        val mileage = facts.firstNormalizedOrValue("ODOMETER")?.let(NormalizationRules::normalizeMileage)
            ?: existingEvent?.mileage
        val shopName = facts.firstNormalizedOrValue("SHOP_NAME")?.let(NormalizationRules::normalizeShopName)
            ?: existingEvent?.shopName
        val totalCostMinor = facts.firstNormalizedOrValue("TOTAL_COST")?.let(NormalizationRules::normalizeCostMinor)
            ?: existingEvent?.totalCostMinor
        val currencyCode = facts.firstOrNull { it.factType == "TOTAL_COST" }?.currencyCode
            ?: existingEvent?.currencyCode

        if (date == null && mileage == null && shopName == null && totalCostMinor == null && serviceItems.isEmpty()) {
            return null
        }

        val status = if (markUserVerified || facts.any { it.isUserEdited && it.factType in USER_VERIFIED_FACT_TYPES }) {
            "USER_VERIFIED"
        } else {
            existingEvent?.status ?: "EXTRACTED_UNVERIFIED"
        }

        return MaintenanceEventEntity(
            eventId = existingEvent?.eventId ?: idProvider(),
            vehicleId = document.vehicleId,
            documentId = document.documentId,
            eventDate = date,
            mileage = mileage,
            shopName = shopName,
            totalCostMinor = totalCostMinor,
            currencyCode = currencyCode,
            status = status,
            createdAt = existingEvent?.createdAt ?: now,
            updatedAt = now,
        )
    }

    private fun List<DocumentFactEntity>.firstNormalizedOrValue(factType: String): String? =
        firstOrNull { it.factType == factType }?.normalizedValue
            ?: firstOrNull { it.factType == factType }?.valueText

    companion object {
        private val USER_VERIFIED_FACT_TYPES = setOf("SERVICE_DATE", "ODOMETER", "SHOP_NAME", "TOTAL_COST")
    }
}
