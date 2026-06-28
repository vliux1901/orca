package org.vliux.orca.prototype.cloudflow.data

import androidx.room.Database
import androidx.room.RoomDatabase
import org.vliux.orca.prototype.cloudflow.model.DocumentEntity
import org.vliux.orca.prototype.cloudflow.model.DocumentFactEntity
import org.vliux.orca.prototype.cloudflow.model.DocumentSourceEntity
import org.vliux.orca.prototype.cloudflow.model.ExtractionRunEntity
import org.vliux.orca.prototype.cloudflow.model.MaintenanceEventEntity
import org.vliux.orca.prototype.cloudflow.model.ServiceItemEntity

@Database(
    entities = [
        DocumentEntity::class,
        DocumentSourceEntity::class,
        ExtractionRunEntity::class,
        DocumentFactEntity::class,
        MaintenanceEventEntity::class,
        ServiceItemEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PrototypeDatabase : RoomDatabase() {
    abstract fun documentDao(): PrototypeDocumentDao
}
