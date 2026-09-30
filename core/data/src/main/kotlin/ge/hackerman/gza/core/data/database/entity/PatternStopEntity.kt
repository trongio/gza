package ge.hackerman.gza.core.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index

/**
 * A stop of a pattern in travel order. [seq] is the list index, not a stop id: a loop route
 * can list a stop twice.
 */
@Entity(tableName = "pattern_stops", primaryKeys = ["route_id", "suffix", "seq"], indices = [Index("stop_id")])
internal data class PatternStopEntity(
    @ColumnInfo(name = "route_id") val routeId: String,
    val suffix: String,
    val seq: Int,
    @ColumnInfo(name = "stop_id") val stopId: String
)
