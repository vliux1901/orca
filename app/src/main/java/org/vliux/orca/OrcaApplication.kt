package org.vliux.orca

import android.app.Application
import androidx.room.Room
import androidx.work.WorkManager
import java.io.File
import org.vliux.orca.provider.ProviderRuntimeConfig
import org.vliux.orca.provider.ProviderRuntimeConfigResolver
import org.vliux.orca.data.OrcaDatabase
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.data.RoomDocumentStore
import org.vliux.orca.data.VehicleRepository
import org.vliux.orca.extraction.MaintenanceExtractionEngine
import org.vliux.orca.extraction.OpenAiCloudExtractionAdapter
import org.vliux.orca.extraction.DefaultMaintenanceExtractionEngine
import org.vliux.orca.extraction.StrictJsonExtractionParser
import org.vliux.orca.importing.AppFileStorage
import org.vliux.orca.normalization.ExtractionNormalizer
import org.vliux.orca.worker.DocumentExtractionWorker

class OrcaApplication : Application() {
    val graph: AppGraph by lazy {
        AppGraph(
            app = this,
            database = Room.databaseBuilder(
                applicationContext,
                OrcaDatabase::class.java,
                "orca.db",
            ).addMigrations(OrcaDatabase.MIGRATION_1_2).build(),
            repositoryFactory = {
                DocumentRepository(
                    store = RoomDocumentStore(it),
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
    private val appContext = app.applicationContext
    val repository: DocumentRepository = repositoryFactory(database)
    val vehicleRepository: VehicleRepository = VehicleRepository(database.vehicleDao())
    private val fileStorage = AppFileStorage(appContext)
    private val parser = StrictJsonExtractionParser()
    private val adapter = OpenAiCloudExtractionAdapter(providerConfig.toCloudExtractionConfig())
    val extractionNormalizer = ExtractionNormalizer()
    val extractionEngine: MaintenanceExtractionEngine = DefaultMaintenanceExtractionEngine(adapter, parser)
    val extractionEngineProviderName: String = providerConfig.providerName
    val extractionEngineModelName: String = providerConfig.modelName
    val extractionConfigured: Boolean = providerConfig.isConfigured()
    val providerAuthSource = providerConfig.authSource

    fun resolveSourceFile(relativePath: String): File = fileStorage.resolveFile(relativePath)

    suspend fun queueDocumentExtraction(documentId: String): Boolean {
        if (!extractionConfigured) {
            return false
        }
        repository.markDocumentQueued(documentId)
        WorkManager.getInstance(appContext).enqueue(DocumentExtractionWorker.buildRequest(documentId))
        return true
    }
}
