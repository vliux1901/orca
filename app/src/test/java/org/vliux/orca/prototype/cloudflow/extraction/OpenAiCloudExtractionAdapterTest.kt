package org.vliux.orca.prototype.cloudflow.extraction

import kotlin.io.path.createTempFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.prototype.cloudflow.model.DocumentEntity
import org.vliux.orca.prototype.cloudflow.model.DocumentSourceEntity

class OpenAiCloudExtractionAdapterTest {
    private val adapter = OpenAiCloudExtractionAdapter(
        config = CloudExtractionConfig(
            responsesUrl = "https://api.openai.com/v1/responses",
            apiKey = "secret",
            providerName = "openai",
            modelName = "gpt-5.4",
        ),
    )

    @Test
    fun buildRequestBodyIncludesPromptFilesAndSchema() {
        val imageFile = createTempFile(suffix = ".jpg").toFile().apply {
            writeText("fake image bytes")
        }
        val pdfFile = createTempFile(suffix = ".pdf").toFile().apply {
            writeText("fake pdf bytes")
        }

        val body = adapter.buildRequestBody(
            ExtractionRequest(
                document = document(),
                sources = listOf(
                    sourcePayload(
                        sourceId = "src-1",
                        fileName = "receipt.jpg",
                        mimeType = "image/jpeg",
                        file = imageFile,
                    ),
                    sourcePayload(
                        sourceId = "src-2",
                        fileName = "invoice.pdf",
                        mimeType = "application/pdf",
                        file = pdfFile,
                    ),
                ),
                promptVersion = "prototype-v1",
            ),
        )

        val bodyLower = body.lowercase()
        assertTrue(body.contains("\"model\":\"gpt-5.4\""))
        assertTrue(body.contains("\"type\":\"input_text\""))
        assertTrue(bodyLower.contains("rotate each page or image upright"))
        assertTrue(body.contains("\"type\":\"input_image\""))
        assertTrue(body.contains("data:image/jpeg;base64,"))
        assertTrue(body.contains("\"type\":\"input_file\""))
        assertTrue(body.contains("data:application/pdf;base64,"))
        assertTrue(body.contains("\"type\":\"json_schema\""))
        assertTrue(body.contains("\"source_id\""))
    }

    @Test
    fun extractOutputTextReadsStructuredJsonFromResponsesEnvelope() {
        val output = adapter.extractOutputText(
            """
            {
              "output": [
                {
                  "type": "message",
                  "status": "completed",
                  "content": [
                    {
                      "type": "output_text",
                      "text": "{\"title\":\"Receipt\",\"facts\":[],\"maintenance_event\":null,\"service_items\":[],\"warnings\":[]}"
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            """{"title":"Receipt","facts":[],"maintenance_event":null,"service_items":[],"warnings":[]}""",
            output,
        )
    }

    private fun document() = DocumentEntity(
        documentId = "doc-1",
        vehicleId = "vehicle-1",
        title = "Receipt",
        sourceCount = 2,
        importStatus = "READY",
        extractionStatus = "QUEUED",
        latestRunId = null,
        occurredAt = null,
        createdAt = "2026-06-28T12:00:00Z",
        updatedAt = "2026-06-28T12:00:00Z",
    )

    private fun sourcePayload(
        sourceId: String,
        fileName: String,
        mimeType: String,
        file: java.io.File,
    ) = SourcePayload(
        source = DocumentSourceEntity(
            sourceId = sourceId,
            documentId = "doc-1",
            sourceOrder = if (sourceId == "src-1") 0 else 1,
            fileName = fileName,
            mimeType = mimeType,
            relativePath = "documents/vehicle-1/doc-1/sources/$fileName",
            byteSize = file.length(),
            checksumSha256 = null,
            pageCount = null,
            createdAt = "2026-06-28T12:00:00Z",
        ),
        file = file,
    )
}
