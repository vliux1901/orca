package org.vliux.orca

import android.app.Application
import androidx.room.Room
import java.io.File
import org.vliux.orca.prototype.cloudflow.data.PrototypeDatabase
import org.vliux.orca.prototype.cloudflow.data.PrototypeDocumentRepository
import org.vliux.orca.prototype.cloudflow.extraction.CloudExtractionConfig
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
            extractionConfig = CloudExtractionConfig.fromBuildConfig(),
        )
    }
}

class PrototypeGraph(
    app: Application,
    database: PrototypeDatabase,
    repositoryFactory: (PrototypeDatabase) -> PrototypeDocumentRepository,
    private val extractionConfig: CloudExtractionConfig,
) {
    val repository: PrototypeDocumentRepository = repositoryFactory(database)
    private val fileStorage = AppFileStorage(app.applicationContext)
    private val parser = StrictJsonExtractionParser()
    private val adapter = OpenAiCloudExtractionAdapter(extractionConfig)
    val extractionNormalizer = ExtractionNormalizer()
    val extractionEngine = PrototypeExtractionEngine(adapter, parser)
    val extractionEngineProviderName: String = extractionConfig.providerName
    val extractionEngineModelName: String = extractionConfig.modelName
    val extractionConfigured: Boolean = extractionConfig.isConfigured()

    fun resolveSourceFile(relativePath: String): File = fileStorage.resolveFile(relativePath)
}
