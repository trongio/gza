package ge.hackerman.gza.core.data.database.dao

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * A timetable period joined with one of its stop rows; the row columns are null for a period
 * that has none (at the asked stop). One query, so a flow never mixes two syncs.
 */
@Suppress("LongParameterList") // One property per selected column.
internal class ScheduleRow(
    @ColumnInfo(name = "route_id") val routeId: String,
    val suffix: String,
    @ColumnInfo(name = "period_index") val periodIndex: Int,
    @ColumnInfo(name = "from_day") val fromDay: DayOfWeek,
    @ColumnInfo(name = "to_day") val toDay: DayOfWeek,
    @ColumnInfo(name = "service_dates") val serviceDates: List<LocalDate>,
    val seq: Int?,
    val position: Int?,
    @ColumnInfo(name = "stop_id") val stopId: String?,
    val times: ByteArray?
)

@Dao
internal abstract class ScheduleDao {
    // Large results: a transaction keeps the cursor consistent across window refills.
    @Transaction
    @Query(
        """
        SELECT p.route_id, p.suffix, p.period_index, p.from_day, p.to_day, p.service_dates,
               t.seq, t.position, t.stop_id, t.times
        FROM schedule_periods p
        LEFT JOIN schedule_stop_times t
          ON t.route_id = p.route_id AND t.suffix = p.suffix AND t.period_index = p.period_index
        WHERE p.route_id = :routeId AND p.suffix = :suffix
        ORDER BY p.period_index, t.seq
        """
    )
    abstract fun observeSchedule(routeId: String, suffix: String): Flow<List<ScheduleRow>>

    /**
     * Every period of every (route, pattern) that stops at [stopId], each with only the rows
     * for that stop (two for a loop that passes it twice, none for a period that skips it).
     */
    @Transaction
    @Query(
        """
        SELECT p.route_id, p.suffix, p.period_index, p.from_day, p.to_day, p.service_dates,
               t.seq, t.position, t.stop_id, t.times
        FROM schedule_periods p
        LEFT JOIN schedule_stop_times t
          ON t.route_id = p.route_id AND t.suffix = p.suffix AND t.period_index = p.period_index
          AND t.stop_id = :stopId
        WHERE EXISTS (
          SELECT 1 FROM schedule_stop_times s
          WHERE s.stop_id = :stopId AND s.route_id = p.route_id AND s.suffix = p.suffix
        )
        ORDER BY p.route_id, p.suffix, p.period_index, t.seq
        """
    )
    abstract fun observeAtStop(stopId: String): Flow<List<ScheduleRow>>
}
