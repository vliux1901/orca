package org.vliux.orca.ui.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentImportFlowTest {
    @Test
    fun chooserOverlayTakesPriorityWhenVisible() {
        assertEquals(
            DocumentImportOverlayStep.CHOOSER,
            documentImportOverlayStepFor(
                chooserOpen = true,
                pendingReviewSources = emptyList(),
                pendingScannerReviewSources = emptyList(),
            ),
        )
    }

    @Test
    fun scannerOverlayShownWhenScannerReviewSourcesExist() {
        assertEquals(
            DocumentImportOverlayStep.SCANNER_REVIEW,
            documentImportOverlayStepFor(
                chooserOpen = false,
                pendingReviewSources = null,
                pendingScannerReviewSources = emptyList(),
            ),
        )
    }

    @Test
    fun scannerPagesRouteToScannerReviewBeforeImport() {
        assertEquals(
            PendingImportStageType.SCANNER_REVIEW,
            pendingImportStageTypeFor(
                listOf(
                    "image/jpeg" to ImportAcquisitionMethod.SCAN_DOCUMENT,
                    "image/jpeg" to ImportAcquisitionMethod.SCAN_DOCUMENT,
                ),
            ),
        )
    }

    @Test
    fun pickerImagesRouteToImageReview() {
        assertEquals(
            PendingImportStageType.IMAGE_REVIEW,
            pendingImportStageTypeFor(
                listOf("image/jpeg" to ImportAcquisitionMethod.FILES),
            ),
        )
    }

    @Test
    fun pdfOnlyImportsSkipReview() {
        assertEquals(
            PendingImportStageType.IMPORT_READY,
            pendingImportStageTypeFor(
                listOf("application/pdf" to ImportAcquisitionMethod.FILES),
            ),
        )
    }
}
