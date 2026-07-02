package org.vliux.orca.importing

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceImportOrderingTest {
    @Test
    fun orderUsesNaturalFilenameSortingForMultipleImports() {
        val ordered = SourceImportOrdering.orderDisplayNames(
            listOf("page-10.jpg", "page-2.jpg", "page-1.jpg"),
        )

        assertEquals(
            listOf("page-1.jpg", "page-2.jpg", "page-10.jpg"),
            ordered,
        )
    }

    @Test
    fun orderKeepsOriginalPositionWhenDisplayNamesMatch() {
        val ordered = SourceImportOrdering.orderDisplayNames(
            listOf("scan.jpg", "scan.jpg"),
        )

        assertEquals(listOf("scan.jpg", "scan.jpg"), ordered)
    }
}
