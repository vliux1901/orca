package org.vliux.orca.extraction

import kotlin.io.path.createTempFile
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentSourceEntity

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

        val request = ExtractionRequest(
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
            promptVersion = "phase3-v1",
        )
        val prompt = adapter.buildPrompt(request)
        val body = adapter.buildRequestBody(request, prompt)

        val bodyLower = body.lowercase()
        assertTrue(prompt.contains("document_id=doc-1"))
        assertTrue(prompt.contains("source_id=src-1"))
        assertTrue(prompt.contains("Treat all uploaded source files as one logical maintenance document."))
        assertTrue(prompt.contains("Use source order ascending as the default reading order across files."))
        assertTrue(prompt.contains("source_id=src-1, order=0"))
        assertTrue(prompt.contains("source_id=src-2, order=1"))
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
        occurredOn = null,
        createdAt = Instant.parse("2026-06-28T12:00:00Z"),
        updatedAt = Instant.parse("2026-06-28T12:00:00Z"),
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
            createdAt = Instant.parse("2026-06-28T12:00:00Z"),
        ),
        file = file,
    )
}
