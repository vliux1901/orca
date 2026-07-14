package org.vliux.orca.ui.vehicle

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportDocumentGuidanceMessageTest {
    @Test
    fun buildsMessageWithBoldEmphasisOnlyOnTargetPhrase() {
        val prefix = "In the next screen, you can select"
        val emphasis = "multiple images or PDF files"
        val suffix = ". Orca will import and process them for this vehicle."

        val message = buildImportDocumentGuidanceMessage(
            prefix = prefix,
            emphasis = emphasis,
            suffix = suffix,
        )

        assertEquals("$prefix $emphasis$suffix", message.text)
        assertEquals(1, message.spanStyles.size)

        val boldSpan = message.spanStyles.single()
        assertEquals(prefix.length + 1, boldSpan.start)
        assertEquals(prefix.length + 1 + emphasis.length, boldSpan.end)
        assertEquals(FontWeight.Bold, boldSpan.item.fontWeight)
        assertTrue(boldSpan.start > 0)
        assertTrue(boldSpan.end < message.text.length)
    }
}
