package org.vliux.orca.ui.vehicle

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.vliux.orca.BuildConfig
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.data.FactRepository
import org.vliux.orca.data.SearchRepository
import org.vliux.orca.data.ServiceItemRepository
import org.vliux.orca.data.VehicleRepository
import org.vliux.orca.importing.AppFileStorage
import org.vliux.orca.importing.ImportCaptureFileStore
import org.vliux.orca.settings.ImportDocumentGuidancePreference
import org.vliux.orca.settings.NewUserWizardPreference
import org.vliux.orca.settings.OpenAiApiKeyStore
import org.vliux.orca.worker.DocumentExtractionScheduler

@Composable
fun VehicleHubScreen(
    vehicleRepository: VehicleRepository,
    documentRepository: DocumentRepository,
    documentExtractionScheduler: DocumentExtractionScheduler,
    appFileStorage: AppFileStorage,
    importCaptureFileStore: ImportCaptureFileStore,
    factRepository: FactRepository,
    serviceItemRepository: ServiceItemRepository,
    searchRepository: SearchRepository,
    openAiApiKeyStore: OpenAiApiKeyStore,
    importDocumentGuidancePreference: ImportDocumentGuidancePreference,
    newUserWizardPreference: NewUserWizardPreference,
    modifier: Modifier = Modifier,
) {
    val routeViewModel: VehicleHubViewModel = viewModel()
    val route = routeViewModel.route
    val vehicles by vehicleRepository.observeVehicles().collectAsStateWithLifecycle(initialValue = emptyList())
    val shouldShowWizard = newUserWizardPreference.shouldShowWizard()

    LaunchedEffect(route, vehicles, shouldShowWizard) {
        if (route == VehicleRoute.List && vehicles.isEmpty() && shouldShowWizard) {
            routeViewModel.updateRoute { VehicleRoute.OnboardingWelcome }
        }
    }

    BackHandler(enabled = route.canNavigateBack()) {
        routeViewModel.updateRoute { navigateBack() }
    }

    when (val currentRoute = route) {
        VehicleRoute.List -> VehicleListScreen(
            vehicleRepository = vehicleRepository,
            documentRepository = documentRepository,
            documentExtractionScheduler = documentExtractionScheduler,
            importDocumentGuidancePreference = importDocumentGuidancePreference,
            importCaptureFileStore = importCaptureFileStore,
            modifier = modifier,
            onOpenSettings = {
                routeViewModel.updateRoute {
                    VehicleRoute.Settings
                }
            },
            onOpenDebug = {
                if (BuildConfig.DEBUG) {
                    routeViewModel.updateRoute {
                        VehicleRoute.Debug
                    }
                }
            },
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
            onOpenDocument = { vehicleId, documentId ->
                routeViewModel.updateRoute {
                    VehicleRoute.DocumentDetail(
                        vehicleId = vehicleId,
                        documentId = documentId,
                    )
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
                when (currentRoute.parent) {
                    VehicleRoute.OnboardingVehicles -> {
                        newUserWizardPreference.acknowledgeWizard()
                        routeViewModel.updateRoute {
                            VehicleRoute.OnboardingVehicles
                        }
                    }
                    else -> {
                        routeViewModel.updateRoute {
                            VehicleRoute.Detail(vehicleId = vehicleId)
                        }
                    }
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
            importDocumentGuidancePreference = importDocumentGuidancePreference,
            importCaptureFileStore = importCaptureFileStore,
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
        VehicleRoute.Settings -> SettingsScreen(
            openAiApiKeyStore = openAiApiKeyStore,
            modifier = modifier,
            onBack = {
                routeViewModel.updateRoute { navigateBack() }
            },
        )
        VehicleRoute.Debug -> {
            if (BuildConfig.DEBUG) {
                DebugScreen(
                    modifier = modifier,
                    onBack = {
                        routeViewModel.updateRoute { navigateBack() }
                    },
                    onShowNewUserWizard = {
                        routeViewModel.updateRoute { VehicleRoute.OnboardingWelcome }
                    },
                )
            } else {
                LaunchedEffect(Unit) {
                    routeViewModel.updateRoute { VehicleRoute.List }
                }
            }
        }
        VehicleRoute.OnboardingWelcome -> OnboardingWelcomeScreen(
            modifier = modifier,
            onNext = {
                routeViewModel.updateRoute { VehicleRoute.OnboardingVehicles }
            },
        )
        VehicleRoute.OnboardingVehicles -> OnboardingVehicleSetupScreen(
            vehicleRepository = vehicleRepository,
            modifier = modifier,
            onAddVehicle = {
                routeViewModel.updateRoute {
                    VehicleRoute.Editor(
                        vehicleId = null,
                        parent = VehicleRoute.OnboardingVehicles,
                    )
                }
            },
            onContinue = {
                routeViewModel.updateRoute { VehicleRoute.OnboardingApiKey }
            },
        )
        VehicleRoute.OnboardingApiKey -> OnboardingApiKeyScreen(
            openAiApiKeyStore = openAiApiKeyStore,
            modifier = modifier,
            onBack = {
                routeViewModel.updateRoute { navigateBack() }
            },
            onSkip = {
                routeViewModel.updateRoute { VehicleRoute.List }
            },
            onSaved = {
                routeViewModel.updateRoute { VehicleRoute.OnboardingImport }
            },
        )
        VehicleRoute.OnboardingImport -> OnboardingImportScreen(
            vehicleRepository = vehicleRepository,
            documentRepository = documentRepository,
            documentExtractionScheduler = documentExtractionScheduler,
            importDocumentGuidancePreference = importDocumentGuidancePreference,
            importCaptureFileStore = importCaptureFileStore,
            modifier = modifier,
            onBack = {
                routeViewModel.updateRoute { navigateBack() }
            },
            onFinish = {
                routeViewModel.updateRoute { VehicleRoute.List }
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
    }
}
