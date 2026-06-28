package org.vliux.orca

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import java.io.File
import org.vliux.orca.prototype.cloudflow.data.PrototypeDatabase
import org.vliux.orca.prototype.cloudflow.data.PrototypeDocumentRepository
import org.vliux.orca.prototype.cloudflow.extraction.CloudExtractionConfig
import org.vliux.orca.prototype.cloudflow.extraction.HttpCloudExtractionAdapter
import org.vliux.orca.prototype.cloudflow.extraction.PrototypeExtractionEngine
import org.vliux.orca.prototype.cloudflow.extraction.StrictJsonExtractionParser
import org.vliux.orca.prototype.cloudflow.importing.AppFileStorage
import org.vliux.orca.prototype.cloudflow.worker.PrototypeWorkerFactory

class OrcaApplication : Application(), Configuration.Provider {
    val graph: PrototypeGraph by lazy {
        PrototypeGraph(
            app = this,
            database = Room.databaseBuilder(
                applicationContext,
                PrototypeDatabase::class.java,
                "prototype.db",
            ).build(),
            repositoryFactory = {
                PrototypeDocumentRepository(
                    database = it,
                    fileStorage = AppFileStorage(applicationContext),
                )
            },
            extractionConfig = CloudExtractionConfig.fromBuildConfig(),
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(PrototypeWorkerFactory(graph))
            .build()
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
    private val adapter = HttpCloudExtractionAdapter(extractionConfig)
    val extractionEngine = PrototypeExtractionEngine(adapter, parser)
    val extractionEngineProviderName: String = extractionConfig.providerName
    val extractionEngineModelName: String = extractionConfig.modelName

    fun resolveSourceFile(relativePath: String): File = fileStorage.resolveFile(relativePath)
}
