package org.vliux.orca.data.model

import java.time.Instant
import java.time.LocalDate

data class DocumentListItem(
    val documentId: String,
    val title: String?,
    val sourceCount: Int,
    val importStatus: String,
    val extractionStatus: String,
    val occurredOn: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
