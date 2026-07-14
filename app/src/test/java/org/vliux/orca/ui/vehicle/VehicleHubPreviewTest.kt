package org.vliux.orca.ui.vehicle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleHubPreviewTest {
    @Test
    fun clampPreviewGestureScaleLimitsZoomRange() {
        assertEquals(1f, clampPreviewGestureScale(0.2f))
        assertEquals(2.5f, clampPreviewGestureScale(2.5f))
        assertEquals(4f, clampPreviewGestureScale(6f))
    }

    @Test
    fun clampPreviewGestureOffsetKeepsPanInsideBounds() {
        assertEquals(
            Offset(12f, -5f),
            clampPreviewGestureOffset(
                offset = Offset(40f, -20f),
                maxOffset = Offset(12f, 5f),
            ),
        )
    }

    @Test
    fun maxPreviewGestureOffsetIsZeroAtBaseScale() {
        assertEquals(
            Offset.Zero,
            maxPreviewGestureOffset(
                containerSize = IntSize(1000, 800),
                bitmapSize = IntSize(1200, 600),
                scale = 1f,
            ),
        )
    }
}
