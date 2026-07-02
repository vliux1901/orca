package org.vliux.orca.ui.home

data class FoundationModule(
    val title: String,
    val detail: String,
    val status: String,
)

object FoundationModuleCatalog {
    val modules: List<FoundationModule> = listOf(
        FoundationModule(
            title = "Data Foundation",
            detail = "Room database, repositories, entities, and exported schema are in place.",
            status = "Ready",
        ),
        FoundationModule(
            title = "Importing Foundation",
            detail = "App-private file storage, source ordering, and document import planning are implemented.",
            status = "Ready",
        ),
        FoundationModule(
            title = "Worker Foundation",
            detail = "Background worker scaffolding exists for no-op verification and document extraction.",
            status = "Ready",
        ),
        FoundationModule(
            title = "Provider Foundation",
            detail = "Provider runtime config, request building, and strict JSON parsing are wired behind app-owned types.",
            status = "Ready",
        ),
        FoundationModule(
            title = "Product Surfaces",
            detail = "Vehicle, import, document review, and timeline screens are still pending later phases.",
            status = "Pending",
        ),
    )
}
