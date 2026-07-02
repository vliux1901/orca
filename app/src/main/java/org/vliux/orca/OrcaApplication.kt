package org.vliux.orca

import android.app.Application
import androidx.room.Room
import java.io.File
import org.vliux.orca.provider.ProviderRuntimeConfig
import org.vliux.orca.provider.ProviderRuntimeConfigResolver
import org.vliux.orca.prototype.cloudflow.data.PrototypeDatabase
import org.vliux.orca.prototype.cloudflow.data.PrototypeDocumentRepository
import org.vliux.orca.prototype.cloudflow.extraction.MaintenanceExtractionEngine
import org.vliux.orca.prototype.cloudflow.extraction.OpenAiCloudExtractionAdapter
import org.vliux.orca.prototype.cloudflow.extraction.PrototypeExtractionEngine
import org.vliux.orca.prototype.cloudflow.extraction.StrictJsonExtractionParser
import org.vliux.orca.prototype.cloudflow.importing.AppFileStorage
import org.vliux.orca.prototype.cloudflow.normalization.ExtractionNormalizer

class OrcaApplication : Application() {
    val graph: PrototypeGraph by lazy {
        PrototypeGraph(
            app = this,
            database = Room.databaseBuilder(
                applicationContext,
                PrototypeDatabase::class.java,
                "prototype.db",
            ).addMigrations(PrototypeDatabase.MIGRATION_1_2).build(),
            repositoryFactory = {
                PrototypeDocumentRepository(
                    database = it,
                    fileStorage = AppFileStorage(applicationContext),
                )
            },
            providerConfig = ProviderRuntimeConfigResolver.fromBuildConfig(),
        )
    }
}

class PrototypeGraph(
    app: Application,
    database: PrototypeDatabase,
    repositoryFactory: (PrototypeDatabase) -> PrototypeDocumentRepository,
    private val providerConfig: ProviderRuntimeConfig,
) {
    val repository: PrototypeDocumentRepository = repositoryFactory(database)
    private val fileStorage = AppFileStorage(app.applicationContext)
    private val parser = StrictJsonExtractionParser()
    private val adapter = OpenAiCloudExtractionAdapter(providerConfig.toCloudExtractionConfig())
    val extractionNormalizer = ExtractionNormalizer()
    val extractionEngine: MaintenanceExtractionEngine = PrototypeExtractionEngine(adapter, parser)
    val extractionEngineProviderName: String = providerConfig.providerName
    val extractionEngineModelName: String = providerConfig.modelName
    val extractionConfigured: Boolean = providerConfig.isConfigured()
    val providerAuthSource = providerConfig.authSource

    fun resolveSourceFile(relativePath: String): File = fileStorage.resolveFile(relativePath)
}
