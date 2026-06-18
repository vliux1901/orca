package org.vliux.orca.prototype.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.vliux.orca.prototype.ocr.OcrBlockDraft
import org.vliux.orca.prototype.ocr.OcrLineDraft
import org.vliux.orca.prototype.ocr.OcrResultDraft

class LayoutNormalizerTest {
    @Test
    fun flattenLinesPreservesSourceIndexes() {
        val result = ocrResult(
            listOf(
                listOf("invoice", "date"),
                listOf("oil", "total"),
            ),
        )

        val lines = LayoutNormalizer.flattenLines(result)

        assertEquals(listOf("invoice", "date", "oil", "total"), lines.map { it.text })
        assertEquals(listOf(0, 0, 1, 1), lines.map { it.sourceBlockIndex })
        assertEquals(listOf(0, 1, 0, 1), lines.map { it.sourceLineIndex })
    }

    @Test
    fun groupLinesIntoRowsWithoutBoundsKeepsLinesSeparate() {
        val lines = listOf(
            NormalizedLine("service", bounds = null, sourceBlockIndex = 0, sourceLineIndex = 0),
            NormalizedLine("total", bounds = null, sourceBlockIndex = 0, sourceLineIndex = 1),
        )

        val rows = LayoutNormalizer.groupLinesIntoRows(lines)

        assertEquals(2, rows.size)
        assertEquals("service", rows[0].text)
        assertEquals("total", rows[1].text)
        assertNull(rows[0].bounds)
        assertNull(rows[1].bounds)
    }

    @Test
    fun normalizeRowsUsesAllOcrLines() {
        val result = ocrResult(listOf(listOf("a"), listOf("b", "c")))

        val rows = LayoutNormalizer.normalizeRows(result)

        assertEquals(listOf("a", "b", "c"), rows.map { it.text })
    }

    private fun ocrResult(blockLines: List<List<String>>): OcrResultDraft {
        return OcrResultDraft(
            fullText = blockLines.flatten().joinToString("\n"),
            blocks = blockLines.map { lines ->
                OcrBlockDraft(
                    text = lines.joinToString("\n"),
                    bounds = null,
                    lines = lines.map { line ->
                        OcrLineDraft(
                            text = line,
                            bounds = null,
                            elements = emptyList(),
                        )
                    },
                )
            },
            elapsedMs = 10L,
        )
    }
}
