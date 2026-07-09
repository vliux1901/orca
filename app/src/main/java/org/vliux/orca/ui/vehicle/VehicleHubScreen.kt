package org.vliux.orca.ui.vehicle

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.data.FactRepository
import org.vliux.orca.data.SearchRepository
import org.vliux.orca.data.ServiceItemRepository
import org.vliux.orca.data.VehicleRepository
import org.vliux.orca.importing.AppFileStorage
import org.vliux.orca.worker.DocumentExtractionScheduler

@Composable
fun VehicleHubScreen(
    vehicleRepository: VehicleRepository,
    documentRepository: DocumentRepository,
    documentExtractionScheduler: DocumentExtractionScheduler,
    appFileStorage: AppFileStorage,
    factRepository: FactRepository,
    serviceItemRepository: ServiceItemRepository,
    searchRepository: SearchRepository,
    modifier: Modifier = Modifier,
) {
    val routeViewModel: VehicleHubViewModel = viewModel()
    val route = routeViewModel.route
    BackHandler(enabled = route != VehicleRoute.List) {
        routeViewModel.updateRoute { navigateBack() }
    }

    when (val currentRoute = route) {
        VehicleRoute.List -> VehicleListScreen(
            vehicleRepository = vehicleRepository,
            modifier = modifier,
            onOpenSearch = { query ->
                routeViewModel.updateRoute {
                    VehicleRoute.Search(query = query)
                }
            },
            onCreateVehicle = {
                routeViewModel.updateRoute {
                    VehicleRoute.Editor(vehicleId = null)
                }
            },
            onOpenVehicle = { vehicleId ->
                routeViewModel.updateRoute {
                    VehicleRoute.Detail(vehicleId = vehicleId)
                }
            },
        )
        is VehicleRoute.Editor -> VehicleEditorScreen(
            vehicleRepository = vehicleRepository,
            modifier = modifier,
            vehicleId = currentRoute.vehicleId,
            onBack = {
                routeViewModel.updateRoute { navigateBack() }
            },
            onSaved = { vehicleId ->
                routeViewModel.updateRoute {
                    VehicleRoute.Detail(vehicleId = vehicleId)
                }
            },
        )
        is VehicleRoute.Search -> SearchScreen(
            searchRepository = searchRepository,
            modifier = modifier,
            initialQuery = currentRoute.query,
            onBack = {
                routeViewModel.updateRoute { navigateBack() }
            },
            onOpenDocument = { vehicleId, documentId ->
                routeViewModel.updateRoute {
                    VehicleRoute.DocumentDetail(
                        vehicleId = vehicleId,
                        documentId = documentId,
                    )
                }
            },
        )
        is VehicleRoute.Detail -> VehicleDetailScreen(
            vehicleRepository = vehicleRepository,
            documentRepository = documentRepository,
            documentExtractionScheduler = documentExtractionScheduler,
            modifier = modifier,
            vehicleId = currentRoute.vehicleId,
            onBack = {
                routeViewModel.updateRoute { navigateBack() }
            },
            onEditVehicle = {
                routeViewModel.updateRoute {
                    VehicleRoute.Editor(
                        vehicleId = currentRoute.vehicleId,
                        parent = currentRoute,
                    )
                }
            },
            onOpenDocument = { documentId ->
                routeViewModel.updateRoute {
                    VehicleRoute.DocumentDetail(
                        vehicleId = currentRoute.vehicleId,
                        documentId = documentId,
                    )
                }
            },
        )
        is VehicleRoute.DocumentDetail -> DocumentDetailScreen(
            documentRepository = documentRepository,
            appFileStorage = appFileStorage,
            factRepository = factRepository,
            serviceItemRepository = serviceItemRepository,
            modifier = modifier,
            vehicleId = currentRoute.vehicleId,
            documentId = currentRoute.documentId,
            onBack = {
                routeViewModel.updateRoute { navigateBack() }
            },
        )
    }
}
