package ge.hackerman.gza.core.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity

/** One direction of a route. The gateway's default pattern is never stored: it flips daily. */
@Entity(tableName = "patterns", primaryKeys = ["route_id", "suffix"])
internal data class PatternEntity(
    @ColumnInfo(name = "route_id") val routeId: String,
    val suffix: String,
    @ColumnInfo(name = "direction_id") val directionId: Int?,
    @ColumnInfo(name = "first_stop_id") val firstStopId: String?,
    @ColumnInfo(name = "first_stop_name_en") val firstStopNameEn: String?,
    @ColumnInfo(name = "first_stop_name_ka") val firstStopNameKa: String?,
    @ColumnInfo(name = "last_stop_id") val lastStopId: String?,
    @ColumnInfo(name = "last_stop_name_en") val lastStopNameEn: String?,
    @ColumnInfo(name = "last_stop_name_ka") val lastStopNameKa: String?,
    @ColumnInfo(name = "headsign_en") val headsignEn: String?,
    @ColumnInfo(name = "headsign_ka") val headsignKa: String?
)
