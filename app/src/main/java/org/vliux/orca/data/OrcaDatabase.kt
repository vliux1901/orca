package org.vliux.orca.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.data.model.ExtractionRunEntity
import org.vliux.orca.data.model.MaintenanceEventEntity
import org.vliux.orca.data.model.SearchEntryEntity
import org.vliux.orca.data.model.ServiceItemEntity
import org.vliux.orca.data.model.VehicleEntity

@Database(
    entities = [
        VehicleEntity::class,
        DocumentEntity::class,
        DocumentSourceEntity::class,
        ExtractionRunEntity::class,
        DocumentFactEntity::class,
        MaintenanceEventEntity::class,
        ServiceItemEntity::class,
        SearchEntryEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(InstantTypeConverters::class)
abstract class OrcaDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun vehicleDao(): VehicleDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vehicles_new` (
                        `vehicleId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `make` TEXT NOT NULL,
                        `model` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `licensePlate` TEXT,
                        `vin` TEXT,
                        `currentMileage` INTEGER,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT NOT NULL,
                        PRIMARY KEY(`vehicleId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `vehicles_new` (
                        `vehicleId`,
                        `displayName`,
                        `make`,
                        `model`,
                        `year`,
                        `licensePlate`,
                        `vin`,
                        `currentMileage`,
                        `createdAt`,
                        `updatedAt`
                    )
                    SELECT
                        `vehicleId`,
                        `displayName`,
                        `make`,
                        `model`,
                        `year`,
                        `licensePlate`,
                        `vin`,
                        `currentMileage`,
                        `createdAt`,
                        `updatedAt`
                    FROM `vehicles`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `vehicles`")
                db.execSQL("ALTER TABLE `vehicles_new` RENAME TO `vehicles`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vehicles_updatedAt` ON `vehicles` (`updatedAt`)")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `extraction_runs_new` (
                        `runId` TEXT NOT NULL,
                        `documentId` TEXT NOT NULL,
                        `backendType` TEXT NOT NULL,
                        `providerName` TEXT NOT NULL,
                        `modelName` TEXT NOT NULL,
                        `promptVersion` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `errorCode` TEXT,
                        `startedAt` TEXT,
                        `completedAt` TEXT,
                        `createdAt` TEXT NOT NULL,
                        PRIMARY KEY(`runId`),
                        FOREIGN KEY(`documentId`) REFERENCES `documents`(`documentId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `extraction_runs_new` (
                        `runId`,
                        `documentId`,
                        `backendType`,
                        `providerName`,
                        `modelName`,
                        `promptVersion`,
                        `status`,
                        `errorCode`,
                        `startedAt`,
                        `completedAt`,
                        `createdAt`
                    )
                    SELECT
                        `runId`,
                        `documentId`,
                        `backendType`,
                        `providerName`,
                        `modelName`,
                        `promptVersion`,
                        `status`,
                        `errorCode`,
                        `startedAt`,
                        `completedAt`,
                        `createdAt`
                    FROM `extraction_runs`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `extraction_runs`")
                db.execSQL("ALTER TABLE `extraction_runs_new` RENAME TO `extraction_runs`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_extraction_runs_documentId` ON `extraction_runs` (`documentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_extraction_runs_status` ON `extraction_runs` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_extraction_runs_createdAt` ON `extraction_runs` (`createdAt`)")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `document_facts` ADD COLUMN `interpretedFromFactId` TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_document_facts_interpretedFromFactId` ON `document_facts` (`interpretedFromFactId`)",
                )
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `document_facts` ADD COLUMN `reviewWarning` TEXT")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `document_facts` ADD COLUMN `userNote` TEXT")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_document_facts_documentId_factType_isActive`
                    ON `document_facts` (`documentId`, `factType`, `isActive`)
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `color` TEXT")
                db.execSQL("ALTER TABLE `vehicles` ADD COLUMN `photoUri` TEXT")
            }
        }
    }
}
