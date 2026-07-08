package org.vliux.orca.data

import java.time.LocalDate

data class VehicleMaintenanceHistoryEntry(
    val category: String,
    val label: String,
    val eventDate: LocalDate?,
    val documentId: String,
)

data class VehicleMaintenanceSummaryItem(
    val component: String,
    val label: String,
    val eventDate: LocalDate?,
    val documentId: String,
)
