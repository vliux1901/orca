package org.vliux.orca.data
import org.vliux.orca.data.model.ServiceItemEntity
import org.vliux.orca.normalization.NormalizationRules

data class SaveServiceItemRequest(
    val label: String,
    val category: String,
    val notes: String? = null,
    val costText: String? = null,
)

class ServiceItemRepository(
    private val store: DocumentStore,
    private val derivedStateRebuilder: DocumentDerivedStateRebuilder,
) {
    suspend fun updateServiceItem(
        documentId: String,
        serviceItemId: String,
        request: SaveServiceItemRequest,
    ) {
        val existing = requireNotNull(store.getServiceItem(serviceItemId)) { "Unknown serviceItemId: $serviceItemId" }
        val event = requireNotNull(store.getEvent(documentId)) { "Document does not have a maintenance event." }
        require(existing.eventId == event.eventId) { "Service item does not belong to documentId=$documentId" }

        val normalizedLabel = NormalizationRules.normalizeWhitespace(request.label)
            ?: error("Service item label cannot be blank.")
        val normalizedNotes = NormalizationRules.normalizeWhitespace(request.notes)
        val normalizedCategory = NormalizationRules.normalizeServiceCategory(
            rawCategory = request.category,
            label = normalizedLabel,
            notes = normalizedNotes,
        ) ?: error("Service item category cannot be blank.")
        val costMinor = request.costText
            ?.takeIf { it.isNotBlank() }
            ?.let { value -> NormalizationRules.normalizeCostMinor(value) ?: error("Invalid service item cost.") }

        val updated = existing.copy(
            category = normalizedCategory,
            label = normalizedLabel,
            notes = normalizedNotes,
            costMinor = costMinor,
            currencyCode = if (costMinor != null) {
                existing.currencyCode ?: event.currencyCode
            } else {
                existing.currencyCode
            },
            createdAt = existing.createdAt,
        )

        store.inTransaction {
            insertServiceItems(listOf(updated))
        }
        derivedStateRebuilder.rebuild(documentId = documentId, markUserVerified = true)
    }

    suspend fun deleteServiceItem(
        documentId: String,
        serviceItemId: String,
    ) {
        val existing = requireNotNull(store.getServiceItem(serviceItemId)) { "Unknown serviceItemId: $serviceItemId" }
        val event = requireNotNull(store.getEvent(documentId)) { "Document does not have a maintenance event." }
        require(existing.eventId == event.eventId) { "Service item does not belong to documentId=$documentId" }

        store.inTransaction {
            deleteServiceItemById(serviceItemId)
        }
        derivedStateRebuilder.rebuild(documentId = documentId, markUserVerified = true)
    }
}
