package org.vliux.orca.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.data.DocumentStore
import org.vliux.orca.data.OrcaDatabase
import org.vliux.orca.data.RoomDocumentStore
import org.vliux.orca.data.VehicleRepository
import org.vliux.orca.extraction.CloudExtractionAdapter
import org.vliux.orca.extraction.DefaultMaintenanceExtractionEngine
import org.vliux.orca.extraction.MaintenanceExtractionEngine
import org.vliux.orca.extraction.OpenAiCloudExtractionAdapter
import org.vliux.orca.extraction.StrictJsonExtractionParser
import org.vliux.orca.importing.AppFileStorage
import org.vliux.orca.normalization.ExtractionNormalizer
import org.vliux.orca.provider.ProviderRuntimeConfig
import org.vliux.orca.provider.ProviderRuntimeConfigResolver

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideProviderRuntimeConfig(): ProviderRuntimeConfig = ProviderRuntimeConfigResolver.fromBuildConfig()

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext appContext: Context,
    ): OrcaDatabase = Room.databaseBuilder(
        appContext,
        OrcaDatabase::class.java,
        "orca.db",
    ).addMigrations(OrcaDatabase.MIGRATION_1_2).build()

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
    @Singleton
    fun provideDocumentRepository(
        store: DocumentStore,
        fileStorage: AppFileStorage,
    ): DocumentRepository = DocumentRepository(
        store = store,
        fileStorage = fileStorage,
    )

    @Provides
    @Singleton
    fun provideVehicleRepository(
        database: OrcaDatabase,
    ): VehicleRepository = VehicleRepository(database.vehicleDao())

    @Provides
    @Singleton
    fun provideStrictJsonExtractionParser(): StrictJsonExtractionParser = StrictJsonExtractionParser()

    @Provides
    @Singleton
    fun provideCloudExtractionAdapter(
        providerRuntimeConfig: ProviderRuntimeConfig,
    ): CloudExtractionAdapter = OpenAiCloudExtractionAdapter(providerRuntimeConfig.toCloudExtractionConfig())

    @Provides
    @Singleton
    fun provideMaintenanceExtractionEngine(
        adapter: CloudExtractionAdapter,
        parser: StrictJsonExtractionParser,
    ): MaintenanceExtractionEngine = DefaultMaintenanceExtractionEngine(adapter, parser)

    @Provides
    @Singleton
    fun provideExtractionNormalizer(): ExtractionNormalizer = ExtractionNormalizer()

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext appContext: Context,
    ): WorkManager = WorkManager.getInstance(appContext)
}
