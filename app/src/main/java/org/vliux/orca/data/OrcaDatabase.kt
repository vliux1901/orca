package org.vliux.orca.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 1,
    exportSchema = true,
)
@TypeConverters(InstantTypeConverters::class)
abstract class OrcaDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun vehicleDao(): VehicleDao
}
