package org.vliux.orca.ui.vehicle

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
}
