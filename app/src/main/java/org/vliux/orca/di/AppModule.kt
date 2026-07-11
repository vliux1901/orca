package org.vliux.orca.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.data.DocumentDerivedStateRebuilder
import org.vliux.orca.data.DocumentStore
import org.vliux.orca.data.FactRepository
import org.vliux.orca.data.OrcaDatabase
import org.vliux.orca.data.RoomDocumentStore
import org.vliux.orca.data.SearchRepository
import org.vliux.orca.data.ServiceItemRepository
import org.vliux.orca.data.VehicleRepository
import org.vliux.orca.search.SearchAnswerEngine
import org.vliux.orca.extraction.CloudExtractionAdapter
import org.vliux.orca.extraction.DefaultMaintenanceExtractionEngine
import org.vliux.orca.extraction.MaintenanceExtractionEngine
import org.vliux.orca.extraction.OpenAiCloudExtractionAdapter
import org.vliux.orca.extraction.StrictJsonExtractionParser
import org.vliux.orca.importing.AppFileStorage
import org.vliux.orca.importing.DocumentImportPlanner
import org.vliux.orca.normalization.ExtractionNormalizer
import org.vliux.orca.provider.ProviderRuntimeConfigResolver
import org.vliux.orca.provider.ProviderRuntimeConfigStore
import org.vliux.orca.search.SearchIndexBuilder
import org.vliux.orca.settings.ImportDocumentGuidancePreference
import org.vliux.orca.settings.OpenAiApiKeyStore
import org.vliux.orca.settings.KeystoreOpenAiApiKeyStore
import org.vliux.orca.settings.NewUserWizardPreference
import org.vliux.orca.settings.SharedPreferencesImportDocumentGuidancePreference
import org.vliux.orca.settings.SharedPreferencesNewUserWizardPreference
import org.vliux.orca.worker.DocumentExtractionWorkEnqueuer
import org.vliux.orca.worker.WorkManagerDocumentExtractionWorkEnqueuer

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideOpenAiApiKeyStore(
        @ApplicationContext appContext: Context,
    ): OpenAiApiKeyStore = KeystoreOpenAiApiKeyStore(appContext)

    @Provides
    @Singleton
    fun provideImportDocumentGuidancePreference(
        @ApplicationContext appContext: Context,
    ): ImportDocumentGuidancePreference = SharedPreferencesImportDocumentGuidancePreference(appContext)

    @Provides
    @Singleton
    fun provideNewUserWizardPreference(
        @ApplicationContext appContext: Context,
    ): NewUserWizardPreference = SharedPreferencesNewUserWizardPreference(appContext)

    @Provides
    @Singleton
    fun provideProviderRuntimeConfigStore(
        openAiApiKeyStore: OpenAiApiKeyStore,
    ): ProviderRuntimeConfigStore = ProviderRuntimeConfigStore(
        metadata = ProviderRuntimeConfigResolver.metadataFromBuildConfig(),
        openAiApiKeyStore = openAiApiKeyStore,
    )

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext appContext: Context,
    ): OrcaDatabase = Room.databaseBuilder(
        appContext,
        OrcaDatabase::class.java,
        "orca.db",
    ).addMigrations(
        OrcaDatabase.MIGRATION_1_2,
        OrcaDatabase.MIGRATION_2_3,
        OrcaDatabase.MIGRATION_3_4,
        OrcaDatabase.MIGRATION_4_5,
        OrcaDatabase.MIGRATION_5_6,
        OrcaDatabase.MIGRATION_6_7,
        OrcaDatabase.MIGRATION_7_8,
    ).build()

    @Provides
    @Singleton
    fun provideDocumentStore(
        database: OrcaDatabase,
    ): DocumentStore = RoomDocumentStore(database)

    @Provides
    @Singleton
    fun provideAppFileStorage(
        @ApplicationContext appContext: Context,
    ): AppFileStorage = AppFileStorage(appContext)

    @Provides
    @Reusable
    fun provideDocumentImportPlanner(): DocumentImportPlanner = DocumentImportPlanner()

    @Provides
    @Singleton
    fun provideDocumentRepository(
        store: DocumentStore,
        fileStorage: AppFileStorage,
        planner: DocumentImportPlanner,
    ): DocumentRepository = DocumentRepository(
        store = store,
        fileStorage = fileStorage,
        planner = planner,
    )

    @Provides
    @Singleton
    fun provideVehicleRepository(
        database: OrcaDatabase,
    ): VehicleRepository = VehicleRepository(
        dao = database.vehicleDao(),
        observeVehicleMaintenanceHistory = database.documentDao()::observeVehicleMaintenanceHistory,
    )

    @Provides
    @Reusable
    fun provideStrictJsonExtractionParser(): StrictJsonExtractionParser = StrictJsonExtractionParser()

    @Provides
    @Singleton
    fun provideCloudExtractionAdapter(
        providerRuntimeConfigStore: ProviderRuntimeConfigStore,
    ): CloudExtractionAdapter = OpenAiCloudExtractionAdapter(providerRuntimeConfigStore::currentConfig)

    @Provides
    @Singleton
    fun provideMaintenanceExtractionEngine(
        adapter: CloudExtractionAdapter,
        parser: StrictJsonExtractionParser,
    ): MaintenanceExtractionEngine = DefaultMaintenanceExtractionEngine(adapter, parser)

    @Provides
    @Reusable
    fun provideExtractionNormalizer(): ExtractionNormalizer = ExtractionNormalizer()

    @Provides
    @Reusable
    fun provideSearchIndexBuilder(): SearchIndexBuilder = SearchIndexBuilder()

    @Provides
    @Reusable
    fun provideSearchAnswerEngine(): SearchAnswerEngine = SearchAnswerEngine()

    @Provides
    @Singleton
    fun provideSearchRepository(
        database: OrcaDatabase,
        searchAnswerEngine: SearchAnswerEngine,
    ): SearchRepository = SearchRepository(
        dao = database.documentDao(),
        answerEngine = searchAnswerEngine,
    )

    @Provides
    @Singleton
    fun provideDocumentDerivedStateRebuilder(
        store: DocumentStore,
        searchIndexBuilder: SearchIndexBuilder,
    ): DocumentDerivedStateRebuilder = DocumentDerivedStateRebuilder(
        store = store,
        searchIndexBuilder = searchIndexBuilder,
    )

    @Provides
    @Singleton
    fun provideFactRepository(
        store: DocumentStore,
        derivedStateRebuilder: DocumentDerivedStateRebuilder,
    ): FactRepository = FactRepository(
        store = store,
        derivedStateRebuilder = derivedStateRebuilder,
    )

    @Provides
    @Singleton
    fun provideServiceItemRepository(
        store: DocumentStore,
        derivedStateRebuilder: DocumentDerivedStateRebuilder,
    ): ServiceItemRepository = ServiceItemRepository(
        store = store,
        derivedStateRebuilder = derivedStateRebuilder,
    )

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext appContext: Context,
    ): WorkManager = WorkManager.getInstance(appContext)

    @Provides
    @Singleton
    fun provideDocumentExtractionWorkEnqueuer(
        workManager: WorkManager,
    ): DocumentExtractionWorkEnqueuer = WorkManagerDocumentExtractionWorkEnqueuer(workManager)
}
