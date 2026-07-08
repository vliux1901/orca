package org.vliux.orca.ui.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleRouteStateTest {
    @Test
    fun documentDetailRouteRoundTripsThroughSavedStateEncoding() {
        val route: VehicleRoute = VehicleRoute.DocumentDetail(
            vehicleId = "vehicle-123",
            documentId = "document-456",
        )

        val restored = listOf("document", "vehicle-123", "document-456").toVehicleRouteOrNull()

        assertEquals(route, restored)
        assertEquals("document:vehicle-123:document-456", route.serializeForSave())
    }

    @Test
    fun editorRouteRoundTripsWithParentRoute() {
        val route: VehicleRoute = VehicleRoute.Editor(
            vehicleId = "vehicle-123",
            parent = VehicleRoute.Detail("vehicle-123"),
        )

        val restored = listOf("editor", "vehicle-123", "detail:vehicle-123").toVehicleRouteOrNull()

        assertEquals(route, restored)
        assertEquals("editor:vehicle-123:detail:vehicle-123", route.serializeForSave())
    }

    @Test
    fun invalidSavedStateReturnsNull() {
        assertNull(listOf("detail").toVehicleRouteOrNull())
        assertNull("document:vehicle-only".deserializeVehicleRouteOrNull())
    }
}
