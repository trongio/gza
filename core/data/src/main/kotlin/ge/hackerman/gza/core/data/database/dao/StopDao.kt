package ge.hackerman.gza.core.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import ge.hackerman.gza.core.data.database.entity.StopEntity
import ge.hackerman.gza.core.data.database.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class StopDao {
    @Query("SELECT * FROM stops ORDER BY id")
    abstract fun observeAll(): Flow<List<StopEntity>>

    @Query("SELECT * FROM stops WHERE id = :id")
    abstract fun observe(id: String): Flow<StopEntity?>

    @Query("SELECT * FROM stops WHERE id = :id")
    abstract suspend fun get(id: String): StopEntity?

    @Query("SELECT * FROM stops WHERE code = :code ORDER BY id")
    abstract suspend fun getByCode(code: String): List<StopEntity>

    @Query("SELECT COUNT(*) FROM stops")
    abstract suspend fun count(): Int

    /** Keeps an existing row, and so its names in both languages. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(stops: List<StopEntity>)

    @Insert
    abstract suspend fun insertAll(stops: List<StopEntity>)

    @Query("DELETE FROM stops")
    abstract suspend fun deleteAll()

    @Upsert
    abstract suspend fun upsertSyncState(state: SyncStateEntity)

    /**
     * Replaces every stop in one transaction, so readers see the old set or the new one, never
     * a mix or an empty table. Delete all then insert, rather than deleting the ids not in the
     * new list: binding 2,753 ids would overflow SQLite's 999 variables on older Androids.
     */
    @Transaction
    open suspend fun replaceAll(stops: List<StopEntity>, syncState: SyncStateEntity) {
        deleteAll()
        insertAll(stops)
        upsertSyncState(syncState)
    }
}
