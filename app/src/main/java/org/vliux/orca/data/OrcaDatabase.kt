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
    version = 2,
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
    }
}
