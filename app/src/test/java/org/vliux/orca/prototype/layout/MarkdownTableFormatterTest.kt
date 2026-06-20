package org.vliux.orca.prototype.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTableFormatterTest {
    @Test
    fun formatReturnsEmptyTextForNoRows() {
        assertEquals("", MarkdownTableFormatter.format(emptyList()))
    }

    @Test
    fun formatUsesWidestTableRowForMarkdownColumns() {
        val rows = listOf(
            row("Service", "Qty", "Cost"),
            row("Oil change", "1", "$89.42"),
            row("Total", "$89.42"),
        )

        val table = MarkdownTableFormatter.format(rows)

        assertEquals(
            """
            | Column 1 | Column 2 | Column 3 |
            | --- | --- | --- |
            | Service | Qty | Cost |
            | Oil change | 1 | $89.42 |
            | Total | $89.42 |  |
            """.trimIndent(),
            table,
        )
    }

    @Test
    fun formatLeavesNonTableRowsAsPlainText() {
        val rows = listOf(
            row("Bay Area Auto"),
            row("Service", "Qty", "Cost"),
            row("Oil change", "1", "$89.42"),
            row("Thank you"),
        )

        val text = MarkdownTableFormatter.format(rows)

        assertEquals(
            """
            Bay Area Auto
            | Column 1 | Column 2 | Column 3 |
            | --- | --- | --- |
            | Service | Qty | Cost |
            | Oil change | 1 | $89.42 |
            Thank you
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun formatDoesNotWrapSingleCellRowsInMarkdownTable() {
        val rows = listOf(
            row("Invoice #123"),
            row("Date 2026-06-19"),
        )

        val text = MarkdownTableFormatter.format(rows)

        assertEquals(
            """
            Invoice #123
            Date 2026-06-19
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun formatEscapesMarkdownCellSeparators() {
        val rows = listOf(row("Engine | cabin filter", "done\nverified"))

        val table = MarkdownTableFormatter.format(rows)

        assertEquals(
            """
            | Column 1 | Column 2 |
            | --- | --- |
            | Engine \| cabin filter | done verified |
            """.trimIndent(),
            table,
        )
    }

    private fun row(vararg cells: String): NormalizedRow {
        val lines = cells.mapIndexed { index, cell ->
            NormalizedLine(
                text = cell,
                bounds = null,
                sourceBlockIndex = 0,
                sourceLineIndex = index,
            )
        }
        return NormalizedRow(
            text = cells.joinToString("  "),
            bounds = null,
            lines = lines,
        )
    }
}
