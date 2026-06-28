package org.vliux.orca.prototype.cloudflow.extraction

import java.io.File
import kotlin.io.path.createTempFile
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.prototype.cloudflow.model.DocumentEntity
import org.vliux.orca.prototype.cloudflow.model.DocumentSourceEntity

class HttpCloudExtractionAdapterTest {
    @Test
    fun extractPostsRequestJsonAndOriginalFiles() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"facts":[],"warnings":["ok"]}"""),
        )
        server.start()

        val sourceFile = createTempFile(suffix = ".jpg").toFile().apply {
            writeText("fake image bytes")
        }
        val adapter = HttpCloudExtractionAdapter(
            config = CloudExtractionConfig(
                baseUrl = server.url("/extract").toString(),
                apiKey = "secret",
                providerName = "generic",
                modelName = "vision-test",
            ),
            client = OkHttpClient(),
        )

        val response = adapter.extract(
            ExtractionRequest(
                document = DocumentEntity(
                    documentId = "doc-1",
                    vehicleId = "vehicle-1",
                    title = "Receipt",
                    sourceCount = 1,
                    importStatus = "READY",
                    extractionStatus = "QUEUED",
                    latestRunId = null,
                    occurredAt = null,
                    createdAt = "2026-06-28T12:00:00Z",
                    updatedAt = "2026-06-28T12:00:00Z",
                ),
                sources = listOf(
                    SourcePayload(
                        source = DocumentSourceEntity(
                            sourceId = "src-1",
                            documentId = "doc-1",
                            sourceOrder = 0,
                            fileName = "receipt.jpg",
                            mimeType = "image/jpeg",
                            relativePath = "documents/vehicle-1/doc-1/sources/source-001.jpg",
                            byteSize = sourceFile.length(),
                            checksumSha256 = null,
                            pageCount = null,
                            createdAt = "2026-06-28T12:00:00Z",
                        ),
                        file = sourceFile,
                    ),
                ),
                promptVersion = "prototype-v1",
            ),
        )

        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
        assertTrue(body.contains("request_json"))
        assertTrue(body.contains("receipt.jpg"))
        assertEquals("""{"facts":[],"warnings":["ok"]}""", response.rawJson)

        server.shutdown()
    }
}
