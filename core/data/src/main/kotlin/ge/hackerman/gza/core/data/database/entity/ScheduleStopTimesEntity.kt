package ge.hackerman.gza.core.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index

/**
 * One stop row of a timetable period. [times] are service minutes packed by
 * [ge.hackerman.gza.core.data.database.PackedMinutes]: every reader wants the whole row, and
 * a row per time would be about 20 times larger (docs/tasks/plans/T04.md 3.4).
 */
@Entity(
    tableName = "schedule_stop_times",
    primaryKeys = ["route_id", "suffix", "period_index", "seq"],
    indices = [Index("stop_id")]
)
@Suppress("LongParameterList") // One property per column, like the data class entities.
internal class ScheduleStopTimesEntity(
    @ColumnInfo(name = "route_id") val routeId: String,
    val suffix: String,
    @ColumnInfo(name = "period_index") val periodIndex: Int,
    /** Row index in the response. */
    val seq: Int,
    /** The gateway's own position value, kept as sent. */
    val position: Int,
    @ColumnInfo(name = "stop_id") val stopId: String,
    val times: ByteArray
) {
    // Not a data class: generated equals would compare the array by identity.
    override fun equals(other: Any?): Boolean = other is ScheduleStopTimesEntity &&
        routeId == other.routeId &&
        suffix == other.suffix &&
        periodIndex == other.periodIndex &&
        seq == other.seq &&
        position == other.position &&
        stopId == other.stopId &&
        times.contentEquals(other.times)

    override fun hashCode(): Int = listOf(routeId, suffix, periodIndex, seq, position, stopId, times.contentHashCode())
        .hashCode()

    override fun toString(): String =
        "ScheduleStopTimesEntity($routeId, $suffix, period=$periodIndex, seq=$seq, stop=$stopId, bytes=${times.size})"
}
