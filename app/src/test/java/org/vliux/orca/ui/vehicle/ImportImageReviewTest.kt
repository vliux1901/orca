package org.vliux.orca.ui.vehicle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportImageReviewTest {
    @Test
    fun reviewedImageDisplayNameKeepsBaseNameAndMatchesMimeType() {
        assertEquals("receipt.jpg", reviewedImageDisplayName("receipt.heic", "image/jpeg"))
        assertEquals("invoice.png", reviewedImageDisplayName("invoice.jpeg", "image/png"))
    }

    @Test
    fun reviewedImageDisplayNameFallsBackWhenNameMissing() {
        assertEquals("image.jpg", reviewedImageDisplayName(null, "image/jpeg"))
    }

    @Test
    fun normalizedQuarterTurnsWrapsIntoClockwiseRange() {
        assertEquals(0, normalizedQuarterTurns(4))
        assertEquals(1, normalizedQuarterTurns(5))
        assertEquals(3, normalizedQuarterTurns(-1))
    }

    @Test
    fun clampImportImageScaleLimitsGestureZoomRange() {
        assertEquals(1f, clampImportImageScale(0.5f))
        assertEquals(2.5f, clampImportImageScale(2.5f))
        assertEquals(4f, clampImportImageScale(6f))
    }

    @Test
    fun clampPreviewOffsetKeepsPanInsideAvailableBounds() {
        assertEquals(
            Offset(10f, -6f),
            clampPreviewOffset(
                offset = Offset(40f, -20f),
                maxOffset = Offset(10f, 6f),
            ),
        )
    }

    @Test
    fun maxPreviewOffsetIsZeroWhenImageFitsAtBaseScale() {
        assertEquals(
            Offset.Zero,
            maxPreviewOffset(
                containerSize = IntSize(1000, 800),
                bitmapSize = IntSize(1200, 600),
                scale = 1f,
            ),
        )
    }
}
