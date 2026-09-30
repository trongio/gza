package ge.hackerman.gza.core.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity

@Entity(tableName = "polylines", primaryKeys = ["route_id", "suffix"])
internal data class PolylineEntity(
    @ColumnInfo(name = "route_id") val routeId: String,
    val suffix: String,
    /** Google encoded polyline, precision 1e5, as the gateway sent it. */
    val encoded: String,
    val color: Int?
)
