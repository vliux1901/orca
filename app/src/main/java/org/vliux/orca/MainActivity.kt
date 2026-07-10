package org.vliux.orca

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.data.FactRepository
import org.vliux.orca.data.SearchRepository
import org.vliux.orca.data.ServiceItemRepository
import org.vliux.orca.data.VehicleRepository
import org.vliux.orca.importing.AppFileStorage
import org.vliux.orca.settings.OpenAiApiKeyStore
import org.vliux.orca.ui.theme.OrcaTheme
import org.vliux.orca.ui.vehicle.VehicleHubScreen
import org.vliux.orca.worker.DocumentExtractionScheduler

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var vehicleRepository: VehicleRepository

    @Inject
    lateinit var documentRepository: DocumentRepository

    @Inject
    lateinit var documentExtractionScheduler: DocumentExtractionScheduler

    @Inject
    lateinit var appFileStorage: AppFileStorage

    @Inject
    lateinit var factRepository: FactRepository

    @Inject
    lateinit var serviceItemRepository: ServiceItemRepository

    @Inject
    lateinit var searchRepository: SearchRepository

    @Inject
    lateinit var openAiApiKeyStore: OpenAiApiKeyStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrcaTheme {
                VehicleHubScreen(
                    vehicleRepository = vehicleRepository,
                    documentRepository = documentRepository,
                    documentExtractionScheduler = documentExtractionScheduler,
                    appFileStorage = appFileStorage,
                    factRepository = factRepository,
                    serviceItemRepository = serviceItemRepository,
                    searchRepository = searchRepository,
                    openAiApiKeyStore = openAiApiKeyStore,
                )
            }
        }
    }
}
