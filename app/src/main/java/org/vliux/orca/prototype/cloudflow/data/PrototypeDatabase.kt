package org.vliux.orca.prototype.cloudflow.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = true,
)
abstract class PrototypeDatabase : RoomDatabase() {
    abstract fun documentDao(): PrototypeDocumentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE extraction_runs ADD COLUMN requestPrompt TEXT")
                db.execSQL("ALTER TABLE extraction_runs ADD COLUMN responseEnvelopeJson TEXT")
                db.execSQL("ALTER TABLE extraction_runs ADD COLUMN responseOutputJson TEXT")
                db.execSQL("ALTER TABLE extraction_runs ADD COLUMN normalizationDebugJson TEXT")
            }
        }
    }
}
