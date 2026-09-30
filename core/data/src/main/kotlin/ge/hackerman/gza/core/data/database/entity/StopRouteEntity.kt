package ge.hackerman.gza.core.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index

/** Which routes serve a stop, as `/v2/stops/{id}/routes` said. */
@Entity(tableName = "stop_routes", primaryKeys = ["stop_id", "route_id"], indices = [Index("route_id")])
internal data class StopRouteEntity(
    @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "route_id") val routeId: String
)
