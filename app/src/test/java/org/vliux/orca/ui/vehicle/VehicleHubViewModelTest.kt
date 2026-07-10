package org.vliux.orca.ui.vehicle

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleHubViewModelTest {
    @Test
    fun routePersistsAcrossViewModelRecreation() {
        val savedStateHandle = SavedStateHandle()
        val firstViewModel = VehicleHubViewModel(savedStateHandle)

        firstViewModel.updateRoute {
            VehicleRoute.Detail(vehicleId = "vehicle-123")
        }
        firstViewModel.updateRoute {
            VehicleRoute.DocumentDetail(
                vehicleId = "vehicle-123",
                documentId = "document-456",
            )
        }

        val secondViewModel = VehicleHubViewModel(savedStateHandle)

        assertEquals(
            VehicleRoute.DocumentDetail(
                vehicleId = "vehicle-123",
                documentId = "document-456",
            ),
            secondViewModel.route,
        )
    }

    @Test
    fun settingsRoutePersistsAcrossViewModelRecreation() {
        val savedStateHandle = SavedStateHandle()
        val firstViewModel = VehicleHubViewModel(savedStateHandle)

        firstViewModel.updateRoute {
            VehicleRoute.Settings
        }

        val secondViewModel = VehicleHubViewModel(savedStateHandle)

        assertEquals(VehicleRoute.Settings, secondViewModel.route)
    }
}
