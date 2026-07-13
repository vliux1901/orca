package org.vliux.orca.ui.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCatalogTest {
    @Test
    fun knownVehicleMakesIncludesArnoldClarkBrands() {
        val makes = knownVehicleMakes()

        assertTrue("Toyota" in makes)
        assertTrue("Ford" in makes)
        assertTrue("Volkswagen" in makes)
        assertTrue("CUPRA" in makes)
    }

    @Test
    fun findVehicleMakeCatalogMatchesNormalizedMakeNames() {
        val citroen = findVehicleMakeCatalog("Citroen")
        val skoda = findVehicleMakeCatalog("Skoda")

        assertNotNull(citroen)
        assertNotNull(skoda)
        assertEquals(listOf("Berlingo", "C3", "C3 Aircross", "C4", "C5 Aircross"), citroen?.models)
        assertEquals(listOf("Fabia", "Kamiq", "Karoq", "Kodiaq", "Octavia", "Scala", "Superb"), skoda?.models)
    }

    @Test
    fun hasCatalogModelsIsFalseForBrandsWithoutModelList() {
        assertFalse(hasCatalogModels("Abarth"))
        assertTrue(hasCatalogModels("Toyota"))
    }
}
