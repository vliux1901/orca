package org.vliux.orca.ui.vehicle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

internal class VehicleHubViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var route by mutableStateOf(savedStateHandle.restoreRoute())
        private set

    fun updateRoute(transform: VehicleRoute.() -> VehicleRoute) {
        val next = route.transition(transform)
        route = next
        savedStateHandle[ROUTE_STATE_KEY] = next.toSavedState()
    }

    private fun SavedStateHandle.restoreRoute(): VehicleRoute {
        val savedRoute = get<ArrayList<String>>(ROUTE_STATE_KEY)
        return savedRoute?.toVehicleRouteOrNull() ?: VehicleRoute.List
    }

    private companion object {
        const val ROUTE_STATE_KEY = "vehicle_hub_route"
    }
}

internal fun VehicleRoute.transition(
    transform: VehicleRoute.() -> VehicleRoute,
): VehicleRoute = transform()

internal fun VehicleRoute.toSavedState(): ArrayList<String> = ArrayList(
    when (this) {
        VehicleRoute.List -> listOf("list")
        is VehicleRoute.Detail -> listOf("detail", vehicleId)
        is VehicleRoute.DocumentDetail -> listOf("document", vehicleId, documentId)
        is VehicleRoute.Editor -> listOf(
            "editor",
            vehicleId.orEmpty(),
            parent?.serializeForSave().orEmpty(),
        )
    },
)
