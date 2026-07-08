package org.vliux.orca.data

import java.time.Instant
import java.time.LocalDate

data class SearchQueryResultRow(
    val entryId: String,
    val vehicleId: String,
    val documentId: String,
    val documentTitle: String?,
    val entryType: String,
    val title: String,
    val body: String,
    val sourceId: String?,
    val sourcePage: Int?,
    val occurredOn: LocalDate?,
    val createdAt: Instant,
)

data class QaEventRecord(
    val eventId: String,
    val vehicleId: String,
    val documentId: String,
    val documentTitle: String?,
    val eventDate: LocalDate?,
    val mileage: Int?,
    val shopName: String?,
    val totalCostMinor: Long?,
    val currencyCode: String?,
    val createdAt: Instant,
)

data class QaServiceRecord(
    val serviceItemId: String,
    val eventId: String,
    val vehicleId: String,
    val documentId: String,
    val documentTitle: String?,
    val category: String,
    val label: String,
    val notes: String?,
    val costMinor: Long?,
    val currencyCode: String?,
    val eventDate: LocalDate?,
    val mileage: Int?,
    val shopName: String?,
    val sourceId: String?,
    val sourcePage: Int?,
    val createdAt: Instant,
)

data class QaFactRecord(
    val factId: String,
    val vehicleId: String,
    val documentId: String,
    val documentTitle: String?,
    val factType: String,
    val valueText: String,
    val normalizedValue: String?,
    val sourceId: String?,
    val sourcePage: Int?,
    val isUserEdited: Boolean,
    val createdAt: Instant,
)
