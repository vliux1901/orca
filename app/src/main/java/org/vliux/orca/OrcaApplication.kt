package org.vliux.orca

import android.app.Application
import androidx.room.Room
import java.io.File
import org.vliux.orca.provider.ProviderRuntimeConfig
import org.vliux.orca.provider.ProviderRuntimeConfigResolver
import org.vliux.orca.data.OrcaDatabase
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.data.VehicleRepository
import org.vliux.orca.extraction.MaintenanceExtractionEngine
import org.vliux.orca.extraction.OpenAiCloudExtractionAdapter
import org.vliux.orca.extraction.DefaultMaintenanceExtractionEngine
import org.vliux.orca.extraction.StrictJsonExtractionParser
import org.vliux.orca.importing.AppFileStorage
import org.vliux.orca.normalization.ExtractionNormalizer

class OrcaApplication : Application() {
    val graph: AppGraph by lazy {
        AppGraph(
            app = this,
            database = Room.databaseBuilder(
                applicationContext,
                OrcaDatabase::class.java,
                "orca.db",
            ).build(),
            repositoryFactory = {
                DocumentRepository(
                    database = it,
                    fileStorage = AppFileStorage(applicationContext),
                )
            },
            providerConfig = ProviderRuntimeConfigResolver.fromBuildConfig(),
        )
    }
}

class AppGraph(
    app: Application,
    database: OrcaDatabase,
    repositoryFactory: (OrcaDatabase) -> DocumentRepository,
    private val providerConfig: ProviderRuntimeConfig,
) {
    val repository: DocumentRepository = repositoryFactory(database)
    val vehicleRepository: VehicleRepository = VehicleRepository(database.vehicleDao())
    private val fileStorage = AppFileStorage(app.applicationContext)
    private val parser = StrictJsonExtractionParser()
    private val adapter = OpenAiCloudExtractionAdapter(providerConfig.toCloudExtractionConfig())
    val extractionNormalizer = ExtractionNormalizer()
    val extractionEngine: MaintenanceExtractionEngine = DefaultMaintenanceExtractionEngine(adapter, parser)
    val extractionEngineProviderName: String = providerConfig.providerName
    val extractionEngineModelName: String = providerConfig.modelName
    val extractionConfigured: Boolean = providerConfig.isConfigured()
    val providerAuthSource = providerConfig.authSource

    fun resolveSourceFile(relativePath: String): File = fileStorage.resolveFile(relativePath)
}
