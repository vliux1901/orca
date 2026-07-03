package org.vliux.orca.importing

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.vliux.orca.data.model.DocumentImportStatus
import org.vliux.orca.data.model.ExtractionStatus

class DocumentImportPlannerTest {
    private var nextId = 0

    @Test
    fun planCreatesOrderedSourcePathsForMultipleFiles() {
        val planner = DocumentImportPlanner(
            nowProvider = { Instant.parse("2026-06-28T12:00:00Z") },
            idProvider = { "id-${nextId++}" },
        )

        val plan = planner.plan(
            vehicleId = "vehicle-123",
            imports = listOf(
                SourceImportPlanInput(displayName = "receipt.png", mimeType = "image/png"),
                SourceImportPlanInput(displayName = "invoice.pdf", mimeType = "application/pdf"),
            ),
        )

        assertEquals("id-0", plan.document.documentId)
        assertEquals("receipt.png +1 more", plan.document.title)
        assertEquals(DocumentImportStatus.PENDING.name, plan.document.importStatus)
        assertEquals(ExtractionStatus.NOT_STARTED.name, plan.document.extractionStatus)
        assertEquals(Instant.parse("2026-06-28T12:00:00Z"), plan.document.createdAt)
        assertEquals(Instant.parse("2026-06-28T12:00:00Z"), plan.document.updatedAt)
        assertEquals(
            listOf(
                "documents/vehicle-123/id-0/sources/source-001.png",
                "documents/vehicle-123/id-0/sources/source-002.pdf",
            ),
            plan.sources.map { it.source.relativePath },
        )
        assertEquals(listOf(0, 1), plan.sources.map { it.source.sourceOrder })
        assertEquals(
            listOf(
                Instant.parse("2026-06-28T12:00:00Z"),
                Instant.parse("2026-06-28T12:00:00Z"),
            ),
            plan.sources.map { it.source.createdAt },
        )
    }

    @Test
    fun planFallsBackToMimeTypeExtensionWhenNameHasNoSuffix() {
        val planner = DocumentImportPlanner(
            nowProvider = { Instant.parse("2026-06-28T12:00:00Z") },
            idProvider = { "fixed-id" },
        )

        val plan = planner.plan(
            vehicleId = "vehicle-123",
            imports = listOf(
                SourceImportPlanInput(displayName = "scan", mimeType = "image/jpeg"),
            ),
        )

        assertEquals(
            "documents/vehicle-123/fixed-id/sources/source-001.jpg",
            plan.sources.single().source.relativePath,
        )
    }
}
