package org.vliux.orca.data.model

data class DocumentListItem(
    val documentId: String,
    val title: String?,
    val sourceCount: Int,
    val importStatus: String,
    val extractionStatus: String,
    val occurredAt: String?,
    val createdAt: String,
    val updatedAt: String,
)
