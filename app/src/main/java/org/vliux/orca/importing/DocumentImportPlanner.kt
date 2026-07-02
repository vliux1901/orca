package org.vliux.orca.importing

import java.time.Instant
import java.util.Locale
import java.util.UUID
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentImportStatus
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.data.model.ExtractionStatus

data class SourceImportPlanInput(
    val displayName: String?,
    val mimeType: String?,
)

data class SourceImportPlan(
    val source: DocumentSourceEntity,
    val destinationFileName: String,
)

data class DocumentImportPlan(
    val document: DocumentEntity,
    val sources: List<SourceImportPlan>,
)

class DocumentImportPlanner(
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) {
    fun plan(
        vehicleId: String,
        imports: List<SourceImportPlanInput>,
    ): DocumentImportPlan {
        require(imports.isNotEmpty()) { "At least one source file is required." }

        val now = nowProvider().toString()
        val documentId = idProvider()
        val title = buildTitle(imports)
        val sourcePlans = imports.mapIndexed { index, input ->
            val sourceId = idProvider()
            val extension = determineExtension(input.displayName, input.mimeType)
            val destinationFileName = "source-${(index + 1).toString().padStart(3, '0')}.$extension"
            SourceImportPlan(
                source = DocumentSourceEntity(
                    sourceId = sourceId,
                    documentId = documentId,
                    sourceOrder = index,
                    fileName = input.displayName?.takeIf { it.isNotBlank() } ?: destinationFileName,
                    mimeType = input.mimeType ?: DEFAULT_MIME_TYPE,
                    relativePath = "documents/$vehicleId/$documentId/sources/$destinationFileName",
                    byteSize = 0L,
                    checksumSha256 = null,
                    pageCount = null,
                    createdAt = now,
                ),
                destinationFileName = destinationFileName,
            )
        }

        return DocumentImportPlan(
            document = DocumentEntity(
                documentId = documentId,
                vehicleId = vehicleId,
                title = title,
                sourceCount = 0,
                importStatus = DocumentImportStatus.PENDING.name,
                extractionStatus = ExtractionStatus.NOT_STARTED.name,
                latestRunId = null,
                occurredAt = null,
                createdAt = now,
                updatedAt = now,
            ),
            sources = sourcePlans,
        )
    }

    private fun buildTitle(imports: List<SourceImportPlanInput>): String {
        val firstName = imports.first().displayName?.takeIf { it.isNotBlank() } ?: "Imported document"
        return if (imports.size == 1) {
            firstName
        } else {
            "$firstName +${imports.size - 1} more"
        }
    }

    private fun determineExtension(displayName: String?, mimeType: String?): String {
        val fromName = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.matches(EXTENSION_REGEX) }
        if (fromName != null) {
            return fromName
        }
        return MIME_TO_EXTENSION[mimeType?.lowercase(Locale.US)] ?: DEFAULT_EXTENSION
    }

    companion object {
        private val EXTENSION_REGEX = Regex("[a-z0-9]{2,5}")
        private const val DEFAULT_EXTENSION = "bin"
        private const val DEFAULT_MIME_TYPE = "application/octet-stream"
        private val MIME_TO_EXTENSION = mapOf(
            "application/pdf" to "pdf",
            "image/jpeg" to "jpg",
            "image/jpg" to "jpg",
            "image/png" to "png",
            "image/heic" to "heic",
            "image/heif" to "heif",
            "image/webp" to "webp",
        )
    }
}
