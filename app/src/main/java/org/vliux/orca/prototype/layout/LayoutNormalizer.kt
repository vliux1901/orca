package org.vliux.orca.prototype.layout

import android.graphics.Rect
import org.vliux.orca.prototype.ocr.OcrResultDraft

object LayoutNormalizer {
    fun normalizeRows(ocrResult: OcrResultDraft): List<NormalizedRow> {
        return groupLinesIntoRows(flattenLines(ocrResult))
    }

    fun flattenLines(ocrResult: OcrResultDraft): List<NormalizedLine> {
        return ocrResult.blocks.flatMapIndexed { blockIndex, block ->
            block.lines.mapIndexed { lineIndex, line ->
                NormalizedLine(
                    text = line.text,
                    bounds = line.bounds,
                    sourceBlockIndex = blockIndex,
                    sourceLineIndex = lineIndex,
                )
            }
        }
    }

    fun groupLinesIntoRows(lines: List<NormalizedLine>): List<NormalizedRow> {
        val sorted = lines.sortedWith(
            compareBy<NormalizedLine> { it.bounds?.centerY() ?: Int.MAX_VALUE }
                .thenBy { it.bounds?.left ?: Int.MAX_VALUE },
        )

        val rows = mutableListOf<MutableList<NormalizedLine>>()
        for (line in sorted) {
            val currentRow = rows.lastOrNull()
            if (currentRow != null && currentRow.any { shouldMergeIntoSameRow(it, line) }) {
                currentRow.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }

        return rows.map { rowLines ->
            val ordered = rowLines.sortedBy { it.bounds?.left ?: Int.MAX_VALUE }
            NormalizedRow(
                text = ordered.joinToString("  ") { it.text },
                bounds = unionBounds(ordered.mapNotNull { it.bounds }),
                lines = ordered,
            )
        }
    }

    fun shouldMergeIntoSameRow(a: NormalizedLine, b: NormalizedLine): Boolean {
        val ab = a.bounds ?: return false
        val bb = b.bounds ?: return false

        val verticalOverlap = minOf(ab.bottom, bb.bottom) - maxOf(ab.top, bb.top)
        val minHeight = minOf(ab.height(), bb.height()).coerceAtLeast(1)
        val overlapRatio = verticalOverlap.toFloat() / minHeight

        val centerDistance = kotlin.math.abs(ab.centerY() - bb.centerY())
        val maxCenterDistance = minHeight * 0.60f

        return overlapRatio >= 0.35f || centerDistance <= maxCenterDistance
    }

    private fun unionBounds(bounds: List<Rect>): Rect? {
        if (bounds.isEmpty()) return null
        return Rect(
            bounds.minOf { it.left },
            bounds.minOf { it.top },
            bounds.maxOf { it.right },
            bounds.maxOf { it.bottom },
        )
    }
}
