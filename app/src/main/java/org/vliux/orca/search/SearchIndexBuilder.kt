package org.vliux.orca.search

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.data.model.MaintenanceEventEntity
import org.vliux.orca.data.model.SearchEntryEntity
import org.vliux.orca.data.model.ServiceItemEntity

class SearchIndexBuilder {
    fun build(
        document: DocumentEntity,
        title: String?,
        facts: List<DocumentFactEntity>,
        event: MaintenanceEventEntity?,
        serviceItems: List<ServiceItemEntity>,
        createdAt: Instant,
    ): List<SearchEntryEntity> {
        val entries = mutableListOf<SearchEntryEntity>()
        event?.let {
            entries += buildEventEntry(
                document = document,
                title = title,
                event = it,
                serviceItems = serviceItems,
                createdAt = createdAt,
            )
        }
        entries += serviceItems
            .sortedWith(compareBy(ServiceItemEntity::category, ServiceItemEntity::label, { it.sourcePage ?: Int.MAX_VALUE }))
            .mapIndexed { index, item ->
                buildServiceItemEntry(
                    document = document,
                    event = event,
                    item = item,
                    createdAt = createdAt,
                    ordinal = index,
                )
            }
        entries += facts
            .filterNot(::isHiddenSearchFact)
            .sortedWith(compareBy(DocumentFactEntity::factType, DocumentFactEntity::valueText, { it.sourcePage ?: Int.MAX_VALUE }))
            .mapIndexed { index, fact ->
                buildFactEntry(
                    document = document,
                    fact = fact,
                    createdAt = createdAt,
                    ordinal = index,
                )
            }
        return entries
    }

    private fun buildEventEntry(
        document: DocumentEntity,
        title: String?,
        event: MaintenanceEventEntity,
        serviceItems: List<ServiceItemEntity>,
        createdAt: Instant,
    ): SearchEntryEntity {
        val serviceSummary = serviceItems
            .sortedBy(ServiceItemEntity::label)
            .joinToString(separator = ", ") { it.label }
            .takeIf { it.isNotBlank() }
        val entryTitle = title?.trim()?.takeIf { it.isNotEmpty() }
            ?: event.shopName?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Maintenance event"
        val entryBody = listOfNotNull(
            event.eventDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)?.let { "Date $it" },
            event.mileage?.let { "Mileage $it" },
            event.shopName?.let { "Shop $it" },
            formatMoney(event.totalCostMinor, event.currencyCode)?.let { "Cost $it" },
            serviceSummary?.let { "Services $it" },
        ).joinToString(separator = " • ")
        return SearchEntryEntity(
            entryId = stableId("${document.documentId}|EVENT|$entryTitle|$entryBody"),
            vehicleId = document.vehicleId,
            documentId = document.documentId,
            entryType = "EVENT",
            title = entryTitle,
            body = entryBody,
            sourceId = null,
            sourcePage = null,
            createdAt = createdAt,
        )
    }

    private fun buildServiceItemEntry(
        document: DocumentEntity,
        event: MaintenanceEventEntity?,
        item: ServiceItemEntity,
        createdAt: Instant,
        ordinal: Int,
    ): SearchEntryEntity {
        val entryBody = listOfNotNull(
            humanizeFactOrCategory(item.category),
            item.notes?.takeIf { it.isNotBlank() },
            formatMoney(item.costMinor, item.currencyCode),
            event?.shopName?.let { "Shop $it" },
            event?.eventDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)?.let { "Date $it" },
        ).joinToString(separator = " • ")
        return SearchEntryEntity(
            entryId = stableId("${document.documentId}|SERVICE_ITEM|$ordinal|${item.category}|${item.label}|$entryBody"),
            vehicleId = document.vehicleId,
            documentId = document.documentId,
            entryType = "SERVICE_ITEM",
            title = item.label,
            body = entryBody,
            sourceId = item.sourceId,
            sourcePage = item.sourcePage,
            createdAt = createdAt,
        )
    }

    private fun buildFactEntry(
        document: DocumentEntity,
        fact: DocumentFactEntity,
        createdAt: Instant,
        ordinal: Int,
    ): SearchEntryEntity {
        val entryTitle = humanizeFactOrCategory(fact.factType)
        val entryBody = listOfNotNull(
            fact.valueText.takeIf { it.isNotBlank() },
            fact.normalizedValue?.takeIf { it.isNotBlank() && it != fact.valueText },
            fact.evidenceText?.takeIf { it.isNotBlank() && it != fact.valueText },
        ).joinToString(separator = " • ")
        return SearchEntryEntity(
            entryId = stableId("${document.documentId}|FACT|$ordinal|${fact.factType}|$entryBody|${fact.sourceId}|${fact.sourcePage}"),
            vehicleId = document.vehicleId,
            documentId = document.documentId,
            entryType = "FACT",
            title = entryTitle,
            body = entryBody,
            sourceId = fact.sourceId,
            sourcePage = fact.sourcePage,
            createdAt = createdAt,
        )
    }

    private fun stableId(seed: String): String =
        UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()

    private fun isHiddenSearchFact(fact: DocumentFactEntity): Boolean =
        fact.factType.trim().uppercase() in hiddenFactTypes

    private fun humanizeFactOrCategory(raw: String): String =
        raw.lowercase()
            .split('_')
            .joinToString(" ") { token -> token.replaceFirstChar(Char::uppercaseChar) }

    private fun formatMoney(costMinor: Long?, currencyCode: String?): String? {
        val amount = costMinor ?: return null
        val code = currencyCode?.uppercase()?.takeIf { it.isNotBlank() } ?: "USD"
        return "$code ${(amount / 100)}.${(amount % 100).toString().padStart(2, '0')}"
    }

    private companion object {
        val hiddenFactTypes = setOf("LINE_ITEM", "INTERPRETED_LINE_ITEM")
    }
}
