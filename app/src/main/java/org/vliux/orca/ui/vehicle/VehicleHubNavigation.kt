package org.vliux.orca.ui.vehicle

import androidx.compose.runtime.saveable.Saver
import java.time.format.DateTimeFormatter
import org.vliux.orca.worker.DocumentExtractionQueueResult

internal sealed interface VehicleRoute {
    data object List : VehicleRoute
    data object Settings : VehicleRoute
    data object OnboardingWelcome : VehicleRoute
    data object OnboardingVehicles : VehicleRoute
    data object OnboardingApiKey : VehicleRoute
    data object OnboardingImport : VehicleRoute
    data class Search(val query: String) : VehicleRoute
    data class Editor(
        val vehicleId: String?,
        val parent: VehicleRoute? = null,
    ) : VehicleRoute
    data class Detail(val vehicleId: String) : VehicleRoute
    data class DocumentDetail(
        val vehicleId: String,
        val documentId: String,
    ) : VehicleRoute
}

internal fun VehicleRoute.serializeForSave(): String = when (this) {
    VehicleRoute.List -> "list"
    VehicleRoute.Settings -> "settings"
    VehicleRoute.OnboardingWelcome -> "onboarding:welcome"
    VehicleRoute.OnboardingVehicles -> "onboarding:vehicles"
    VehicleRoute.OnboardingApiKey -> "onboarding:api-key"
    VehicleRoute.OnboardingImport -> "onboarding:import"
    is VehicleRoute.Search -> "search:$query"
    is VehicleRoute.Detail -> "detail:$vehicleId"
    is VehicleRoute.DocumentDetail -> "document:$vehicleId:$documentId"
    is VehicleRoute.Editor -> buildString {
        append("editor:")
        append(vehicleId.orEmpty())
        append(':')
        append(parent?.serializeForSave().orEmpty())
    }
}

internal fun List<String>.toVehicleRouteOrNull(): VehicleRoute? {
    val kind = firstOrNull() ?: return null
    return when (kind) {
        "list" -> VehicleRoute.List
        "settings" -> VehicleRoute.Settings
        "onboarding" -> when (getOrNull(1)) {
            "welcome" -> VehicleRoute.OnboardingWelcome
            "vehicles" -> VehicleRoute.OnboardingVehicles
            "api-key" -> VehicleRoute.OnboardingApiKey
            "import" -> VehicleRoute.OnboardingImport
            else -> null
        }
        "search" -> VehicleRoute.Search(query = getOrNull(1).orEmpty())
        "detail" -> getOrNull(1)?.takeIf { it.isNotEmpty() }?.let(VehicleRoute::Detail)
        "document" -> {
            val vehicleId = getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
            val documentId = getOrNull(2)?.takeIf { it.isNotEmpty() } ?: return null
            VehicleRoute.DocumentDetail(vehicleId = vehicleId, documentId = documentId)
        }
        "editor" -> {
            val vehicleId = getOrNull(1)?.takeIf { it.isNotEmpty() }
            val parent = getOrNull(2)
                ?.takeIf { it.isNotEmpty() }
                ?.deserializeVehicleRouteOrNull()
            VehicleRoute.Editor(vehicleId = vehicleId, parent = parent)
        }
        else -> null
    }
}

internal fun String.deserializeVehicleRouteOrNull(): VehicleRoute? {
    val parts = split(':')
    return when (parts.firstOrNull()) {
        "list" -> VehicleRoute.List
        "settings" -> VehicleRoute.Settings
        "onboarding" -> when (parts.getOrNull(1)) {
            "welcome" -> VehicleRoute.OnboardingWelcome
            "vehicles" -> VehicleRoute.OnboardingVehicles
            "api-key" -> VehicleRoute.OnboardingApiKey
            "import" -> VehicleRoute.OnboardingImport
            else -> null
        }
        "search" -> VehicleRoute.Search(query = parts.drop(1).joinToString(":"))
        "detail" -> parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let(VehicleRoute::Detail)
        "document" -> {
            val vehicleId = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
            val documentId = parts.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: return null
            VehicleRoute.DocumentDetail(vehicleId = vehicleId, documentId = documentId)
        }
        "editor" -> {
            val vehicleId = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
            val parent = parts.drop(2).joinToString(":")
                .takeIf { it.isNotEmpty() }
                ?.deserializeVehicleRouteOrNull()
            VehicleRoute.Editor(vehicleId = vehicleId, parent = parent)
        }
        else -> null
    }
}

internal fun VehicleRoute.logLabel(): String = when (this) {
    VehicleRoute.List -> "list"
    VehicleRoute.Settings -> "settings"
    VehicleRoute.OnboardingWelcome -> "onboarding(welcome)"
    VehicleRoute.OnboardingVehicles -> "onboarding(vehicles)"
    VehicleRoute.OnboardingApiKey -> "onboarding(apiKey)"
    VehicleRoute.OnboardingImport -> "onboarding(import)"
    is VehicleRoute.Search -> "search(queryLength=${query.length})"
    is VehicleRoute.Detail -> "detail(vehicleId=$vehicleId)"
    is VehicleRoute.DocumentDetail -> "document(vehicleId=$vehicleId, documentId=$documentId)"
    is VehicleRoute.Editor -> "editor(vehicleId=$vehicleId,parent=${parent?.logLabel()})"
}

internal fun VehicleRoute.navigateBack(): VehicleRoute = when (this) {
    VehicleRoute.List -> VehicleRoute.List
    VehicleRoute.Settings -> VehicleRoute.List
    VehicleRoute.OnboardingWelcome -> VehicleRoute.List
    VehicleRoute.OnboardingVehicles -> VehicleRoute.OnboardingWelcome
    VehicleRoute.OnboardingApiKey -> VehicleRoute.OnboardingVehicles
    VehicleRoute.OnboardingImport -> VehicleRoute.OnboardingApiKey
    is VehicleRoute.Search -> VehicleRoute.List
    is VehicleRoute.Detail -> VehicleRoute.List
    is VehicleRoute.DocumentDetail -> VehicleRoute.Detail(vehicleId)
    is VehicleRoute.Editor -> when (val parent = parent) {
        null -> VehicleRoute.List
        is VehicleRoute.Detail -> parent
        is VehicleRoute.DocumentDetail -> parent
        is VehicleRoute.Search -> parent
        VehicleRoute.Settings -> VehicleRoute.List
        VehicleRoute.OnboardingWelcome -> VehicleRoute.OnboardingWelcome
        VehicleRoute.OnboardingVehicles -> VehicleRoute.OnboardingVehicles
        VehicleRoute.OnboardingApiKey -> VehicleRoute.OnboardingApiKey
        VehicleRoute.OnboardingImport -> VehicleRoute.OnboardingImport
        VehicleRoute.List -> VehicleRoute.List
        is VehicleRoute.Editor -> VehicleRoute.List
    }
}

internal fun VehicleRoute.canNavigateBack(): Boolean = when (this) {
    VehicleRoute.List -> false
    VehicleRoute.OnboardingWelcome -> false
    else -> true
}

internal val vehicleSummaryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

internal val vehicleFormDraftSaver: Saver<VehicleFormDraft, Any> = Saver(
    save = { draft ->
        listOf(
            draft.displayName,
            draft.make,
            draft.model,
            draft.year,
            draft.licensePlate,
            draft.vin,
            draft.color,
            draft.photoUri.orEmpty(),
            draft.currentMileage,
        )
    },
    restore = { restored ->
        val values = restored as? List<*> ?: return@Saver null
        VehicleFormDraft(
            displayName = values.getOrNull(0) as? String ?: "",
            make = values.getOrNull(1) as? String ?: "",
            model = values.getOrNull(2) as? String ?: "",
            year = values.getOrNull(3) as? String ?: "",
            licensePlate = values.getOrNull(4) as? String ?: "",
            vin = values.getOrNull(5) as? String ?: "",
            color = values.getOrNull(6) as? String ?: "",
            photoUri = (values.getOrNull(7) as? String)?.takeIf { it.isNotBlank() },
            currentMileage = values.getOrNull(8) as? Int,
        )
    },
)

internal data class ImportResult(
    val documentId: String,
    val queueResult: DocumentExtractionQueueResult,
)

internal val SUPPORTED_IMPORT_MIME_TYPES = arrayOf("image/*", "application/pdf")
