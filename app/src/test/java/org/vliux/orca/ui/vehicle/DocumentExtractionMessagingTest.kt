package org.vliux.orca.ui.vehicle

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.data.DocumentDetail
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.data.model.ExtractionRunEntity

class DocumentExtractionMessagingTest {
    @Test
    fun queuedOfflineStatusExplainsBackgroundRetry() {
        val message = extractionStatusMessage(
            detail(
                extractionStatus = "QUEUED",
                latestRun = run(status = "FAILED", errorCode = "PROVIDER_OFFLINE"),
            ),
        )

        assertTrue(message.orEmpty().contains("could not reach the cloud provider"))
        assertTrue(message.orEmpty().contains("background retry"))
    }

    @Test
    fun failureMessageExplainsMalformedProviderResponse() {
        assertEquals(
            "The cloud provider returned data Orca could not validate. Retry the document or inspect the original files for unusual formatting.",
            extractionFailureMessage("MALFORMED_PROVIDER_RESPONSE"),
        )
    }

    @Test
    fun documentSourceSummaryShowsCountAndTotalBytes() {
        assertEquals(
            "2 files | 3.5 KB total",
            documentSourceSummary(
                detail(
                    sources = listOf(
                        source("receipt-1.jpg", 1024),
                        source("receipt-2.jpg", 2560),
                    ),
                ),
            ),
        )
    }

    private fun detail(
        extractionStatus: String = "QUEUED",
        latestRun: ExtractionRunEntity? = null,
        sources: List<DocumentSourceEntity> = listOf(source("receipt.jpg", 1024)),
    ) = DocumentDetail(
        document = DocumentEntity(
            documentId = "doc-1",
            vehicleId = "vehicle-1",
            title = "Receipt",
            sourceCount = sources.size,
            importStatus = "READY",
            extractionStatus = extractionStatus,
            latestRunId = latestRun?.runId,
            occurredOn = null,
            createdAt = Instant.parse("2026-07-09T12:00:00Z"),
            updatedAt = Instant.parse("2026-07-09T12:00:00Z"),
        ),
        sources = sources,
        latestRun = latestRun,
        facts = emptyList(),
        event = null,
        serviceItems = emptyList(),
    )

    private fun source(
        fileName: String,
        byteSize: Long,
    ) = DocumentSourceEntity(
        sourceId = fileName,
        documentId = "doc-1",
        sourceOrder = 0,
        fileName = fileName,
        mimeType = "image/jpeg",
        relativePath = "vehicles/vehicle-1/documents/doc-1/sources/$fileName",
        byteSize = byteSize,
        checksumSha256 = null,
        pageCount = null,
        createdAt = Instant.parse("2026-07-09T12:00:00Z"),
    )

    private fun run(
        status: String,
        errorCode: String?,
    ) = ExtractionRunEntity(
        runId = "run-1",
        documentId = "doc-1",
        backendType = "CLOUD_VISION",
        providerName = "openai",
        modelName = "gpt-test",
        promptVersion = "phase3-v1",
        status = status,
        errorCode = errorCode,
        startedAt = Instant.parse("2026-07-09T12:00:00Z"),
        completedAt = Instant.parse("2026-07-09T12:01:00Z"),
        createdAt = Instant.parse("2026-07-09T12:00:00Z"),
    )
}
