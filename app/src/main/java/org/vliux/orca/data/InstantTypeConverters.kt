package org.vliux.orca.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

class InstantTypeConverters {
    @TypeConverter
    fun instantToIso8601(value: Instant?): String? = value?.toString()

    @TypeConverter
    fun iso8601ToInstant(value: String?): Instant? = value?.let(Instant::parse)

    @TypeConverter
    fun localDateToIso8601(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun iso8601ToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}
