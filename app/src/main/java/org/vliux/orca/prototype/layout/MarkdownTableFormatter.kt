package org.vliux.orca.prototype.layout

object MarkdownTableFormatter {
    fun format(rows: List<NormalizedRow>): String {
        if (rows.isEmpty()) return ""

        return rows
            .fold(mutableListOf<List<NormalizedRow>>()) { groups, row ->
                if (groups.isEmpty() || groups.last().last().isTableRow() != row.isTableRow()) {
                    groups.add(listOf(row))
                } else {
                    groups[groups.lastIndex] = groups.last() + row
                }
                groups
            }
            .joinToString("\n") { group ->
                if (group.first().isTableRow()) {
                    formatTable(group)
                } else {
                    group.joinToString("\n") { row -> row.text.trim() }
                }
            }
    }

    private fun formatTable(rows: List<NormalizedRow>): String {
        val columnCount = rows.maxOf { row -> row.lines.size }
        val header = (1..columnCount).joinToString(
            prefix = "| ",
            separator = " | ",
            postfix = " |",
        ) { columnIndex -> "Column $columnIndex" }
        val separator = List(columnCount) { "---" }.joinToString(
            prefix = "| ",
            separator = " | ",
            postfix = " |",
        )
        val body = rows.joinToString("\n") { row ->
            val cells = row.lines.map { line -> line.text }
            (0 until columnCount).joinToString(
                prefix = "| ",
                separator = " | ",
                postfix = " |",
            ) { cellIndex -> cells.getOrNull(cellIndex)?.toMarkdownCell().orEmpty() }
        }

        return listOf(header, separator, body).joinToString("\n")
    }

    private fun NormalizedRow.isTableRow(): Boolean {
        return lines.size >= 2
    }

    private fun String.toMarkdownCell(): String {
        return replace("\n", " ")
            .replace("|", "\\|")
            .trim()
    }
}
