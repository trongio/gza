package ge.hackerman.gza.core.data.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import ge.hackerman.gza.core.data.database.entity.SyncStateEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE `key` = :key")
    abstract fun observe(key: String): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE `key` = :key")
    abstract suspend fun get(key: String): SyncStateEntity?

    @Upsert
    abstract suspend fun upsert(state: SyncStateEntity)

    /** Only touches a row that exists: a route that never synced has nothing to refresh yet. */
    @Query("UPDATE sync_state SET last_used_at = :at WHERE `key` = :key")
    abstract suspend fun markUsed(key: String, at: Instant): Int

    /** Rows whose key starts with [prefix] and that were used at or after [usedSince]. */
    @Query("SELECT * FROM sync_state WHERE `key` LIKE :prefix || '%' AND last_used_at >= :usedSince ORDER BY `key`")
    abstract suspend fun getUsedSince(prefix: String, usedSince: Instant): List<SyncStateEntity>
}
