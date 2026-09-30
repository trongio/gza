package ge.hackerman.gza.core.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import ge.hackerman.gza.core.data.database.entity.RouteEntity
import ge.hackerman.gza.core.data.database.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class RouteDao {
    @Query("SELECT * FROM routes ORDER BY id")
    abstract fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    abstract fun observe(id: String): Flow<RouteEntity?>

    @Query("SELECT * FROM routes WHERE id = :id")
    abstract suspend fun get(id: String): RouteEntity?

    @Query("SELECT COUNT(*) FROM routes")
    abstract suspend fun count(): Int

    /** Keeps an existing row: the catalog's names win over those from a detail or a stop. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(routes: List<RouteEntity>)

    @Insert
    abstract suspend fun insertAll(routes: List<RouteEntity>)

    @Query("DELETE FROM routes")
    abstract suspend fun deleteAll()

    @Upsert
    abstract suspend fun upsertSyncState(state: SyncStateEntity)

    /** Same contract as [StopDao.replaceAll]. */
    @Transaction
    open suspend fun replaceAll(routes: List<RouteEntity>, syncState: SyncStateEntity) {
        deleteAll()
        insertAll(routes)
        upsertSyncState(syncState)
    }
}
