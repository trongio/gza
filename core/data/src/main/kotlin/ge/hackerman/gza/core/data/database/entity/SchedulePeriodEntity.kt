package ge.hackerman.gza.core.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The days a timetable applies to. [serviceDates] are kept exactly as received, even when
 * they are all in the past: matching by weekday past them is `:core:predict`'s call.
 */
@Entity(tableName = "schedule_periods", primaryKeys = ["route_id", "suffix", "period_index"])
internal data class SchedulePeriodEntity(
    @ColumnInfo(name = "route_id") val routeId: String,
    val suffix: String,
    @ColumnInfo(name = "period_index") val periodIndex: Int,
    @ColumnInfo(name = "from_day") val fromDay: DayOfWeek,
    @ColumnInfo(name = "to_day") val toDay: DayOfWeek,
    @ColumnInfo(name = "service_dates") val serviceDates: List<LocalDate>
)
