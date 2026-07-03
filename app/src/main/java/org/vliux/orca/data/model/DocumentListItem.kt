package org.vliux.orca.data.model

import java.time.Instant

data class DocumentListItem(
    val documentId: String,
    val title: String?,
    val sourceCount: Int,
    val importStatus: String,
    val extractionStatus: String,
    val occurredAt: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
