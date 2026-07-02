package org.vliux.orca.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FoundationModuleCatalogTest {
    @Test
    fun modulesReflectCurrentFoundationState() {
        val modules = FoundationModuleCatalog.modules

        assertEquals(
            listOf(
                "Data Foundation",
                "Importing Foundation",
                "Worker Foundation",
                "Provider Foundation",
                "Product Surfaces",
            ),
            modules.map { it.title },
        )
        assertEquals(listOf("Ready", "Ready", "Ready", "Ready", "Pending"), modules.map { it.status })
        assertFalse(modules.any { module ->
            module.title.contains("prototype", ignoreCase = true) ||
                module.detail.contains("prototype", ignoreCase = true)
        })
    }
}
