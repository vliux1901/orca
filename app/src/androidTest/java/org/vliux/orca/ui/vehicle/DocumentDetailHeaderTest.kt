package org.vliux.orca.ui.vehicle

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.vliux.orca.data.DocumentDetail
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.importing.AppFileStorage

class DocumentDetailHeaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun headerOmitsSourceCountAndThumbnailHelperCopy() {
        val detail = DocumentDetail(
            document = DocumentEntity(
                documentId = "document-1",
                vehicleId = "vehicle-1",
                title = "Dealer receipt",
                sourceCount = 1,
                importStatus = "IMPORTED",
                extractionStatus = "COMPLETED",
                latestRunId = null,
                occurredOn = null,
                createdAt = Instant.parse("2026-07-09T00:00:00Z"),
                updatedAt = Instant.parse("2026-07-09T00:00:00Z"),
            ),
            sources = listOf(
                DocumentSourceEntity(
                    sourceId = "source-1",
                    documentId = "document-1",
                    sourceOrder = 0,
                    fileName = "receipt.jpg",
                    mimeType = "image/jpeg",
                    relativePath = "sources/receipt.jpg",
                    byteSize = 12L,
                    checksumSha256 = null,
                    pageCount = null,
                    createdAt = Instant.parse("2026-07-09T00:00:00Z"),
                ),
            ),
            latestRun = null,
            facts = emptyList(),
            event = null,
            serviceItems = emptyList(),
        )

        composeRule.setContent {
            MaterialTheme {
                DocumentDetailHeader(
                    detail = detail,
                    appFileStorage = AppFileStorage(File("/tmp")) { ByteArrayInputStream(ByteArray(0)) },
                    onOpenSourcePreview = {},
                )
            }
        }

        composeRule.onNodeWithText("Dealer receipt").assertTextContains("Dealer receipt")
        composeRule.onNodeWithText("Imported Images").assertTextContains("Imported Images")
        composeRule.onAllNodesWithText("Tap a thumbnail to inspect the original picture full screen.").assertCountEquals(0)
        composeRule.onAllNodesWithText("Sources 1 | Updated 2026-07-09T00:00:00Z").assertCountEquals(0)
    }
}
