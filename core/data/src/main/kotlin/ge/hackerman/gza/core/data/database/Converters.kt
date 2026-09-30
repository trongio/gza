package ge.hackerman.gza.core.data.database

import androidx.room3.ColumnTypeConverter
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Column types. Instants are UTC epoch millis and days are ISO 1..7, so nothing stored
 * depends on the device's time zone. Service dates are Tbilisi calendar dates as the gateway
 * listed them, joined by commas.
 */
internal object Converters {
    @ColumnTypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @ColumnTypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @ColumnTypeConverter
    fun dayOfWeekToInt(value: DayOfWeek?): Int? = value?.value

    /** Null for anything outside 1..7, never an exception. */
    @ColumnTypeConverter
    fun intToDayOfWeek(value: Int?): DayOfWeek? = value?.takeIf { it in MONDAY..SUNDAY }?.let(DayOfWeek::of)

    @ColumnTypeConverter
    fun datesToText(value: List<LocalDate>?): String? = value?.joinToString(DATE_SEPARATOR)

    /** An unparseable entry is skipped; an empty text is an empty list. */
    @ColumnTypeConverter
    fun textToDates(value: String?): List<LocalDate>? = value?.split(DATE_SEPARATOR)?.mapNotNull(::parseDateOrNull)

    private fun parseDateOrNull(raw: String): LocalDate? = try {
        raw.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    } catch (ignored: DateTimeException) {
        null
    }

    private const val DATE_SEPARATOR = ","
    private const val MONDAY = 1
    private const val SUNDAY = 7
}
