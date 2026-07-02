package org.vliux.orca.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.data.model.ExtractionRunEntity
import org.vliux.orca.data.model.MaintenanceEventEntity
import org.vliux.orca.data.model.ServiceItemEntity

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
abstract class OrcaDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

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
