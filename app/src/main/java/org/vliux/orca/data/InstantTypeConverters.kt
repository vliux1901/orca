package org.vliux.orca.data

import androidx.room.TypeConverter
import java.time.Instant

class InstantTypeConverters {
    @TypeConverter
    fun instantToIso8601(value: Instant?): String? = value?.toString()

    @TypeConverter
    fun iso8601ToInstant(value: String?): Instant? = value?.let(Instant::parse)
}
