package ge.hackerman.gza.core.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import ge.hackerman.gza.core.data.database.entity.RouteEntity
import ge.hackerman.gza.core.data.database.entity.StopRouteEntity
import ge.hackerman.gza.core.data.database.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class StopRoutesDao {
    @Query(
        """
        SELECT routes.* FROM stop_routes
        JOIN routes ON routes.id = stop_routes.route_id
        WHERE stop_routes.stop_id = :stopId
        ORDER BY routes.id
        """
    )
    abstract fun observeRoutes(stopId: String): Flow<List<RouteEntity>>

    @Query("SELECT route_id FROM stop_routes WHERE stop_id = :stopId ORDER BY route_id")
    abstract suspend fun getRouteIds(stopId: String): List<String>

    @Query("DELETE FROM stop_routes WHERE stop_id = :stopId")
    abstract suspend fun deleteForStop(stopId: String)

    @Insert
    abstract suspend fun insertAll(rows: List<StopRouteEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertRoutesIfAbsent(routes: List<RouteEntity>)

    @Upsert
    abstract suspend fun upsertSyncState(state: SyncStateEntity)

    /** [routesIfAbsent] lets the stop's routes show before the weekly catalog has run. */
    @Transaction
    open suspend fun replaceForStop(
        stopId: String,
        rows: List<StopRouteEntity>,
        routesIfAbsent: List<RouteEntity>,
        syncState: SyncStateEntity
    ) {
        deleteForStop(stopId)
        insertAll(rows)
        insertRoutesIfAbsent(routesIfAbsent)
        upsertSyncState(syncState)
    }
}
