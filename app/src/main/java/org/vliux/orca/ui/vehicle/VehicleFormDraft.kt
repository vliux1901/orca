package org.vliux.orca.ui.vehicle

import org.vliux.orca.data.SaveVehicleRequest
import org.vliux.orca.data.model.VehicleEntity

data class VehicleFormDraft(
    val displayName: String = "",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val licensePlate: String = "",
    val vin: String = "",
    val color: String = "",
    val photoUri: String? = null,
    val currentMileage: Int? = null,
) {
    fun validate(): VehicleFormValidation {
        if (displayName.isBlank() || make.isBlank() || model.isBlank() || year.isBlank()) {
            return VehicleFormValidation(errorMessage = "Display name, make, model, and year are required.")
        }

        val parsedYear = year.toIntOrNull()
            ?: return VehicleFormValidation(errorMessage = "Year must be a number.")
        if (parsedYear <= 0) {
            return VehicleFormValidation(errorMessage = "Year must be greater than 0.")
        }

        return VehicleFormValidation(
            request = SaveVehicleRequest(
                displayName = displayName.trim(),
                make = make.trim(),
                model = model.trim(),
                year = parsedYear,
                licensePlate = licensePlate.trim().takeIf { it.isNotEmpty() },
                vin = vin.trim().takeIf { it.isNotEmpty() },
                color = color.trim().takeIf { it.isNotEmpty() },
                photoUri = photoUri?.trim()?.takeIf { it.isNotEmpty() },
                currentMileage = currentMileage,
            ),
        )
    }

    companion object {
        fun fromEntity(vehicle: VehicleEntity): VehicleFormDraft = VehicleFormDraft(
            displayName = vehicle.displayName,
            make = vehicle.make,
            model = vehicle.model,
            year = vehicle.year.toString(),
            licensePlate = vehicle.licensePlate.orEmpty(),
            vin = vehicle.vin.orEmpty(),
            color = vehicle.color.orEmpty(),
            photoUri = vehicle.photoUri,
            currentMileage = vehicle.currentMileage,
        )
    }
}

data class VehicleFormValidation(
    val request: SaveVehicleRequest? = null,
    val errorMessage: String? = null,
)
