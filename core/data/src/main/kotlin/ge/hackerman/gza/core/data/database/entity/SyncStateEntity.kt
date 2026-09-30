package ge.hackerman.gza.core.data.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.time.Instant

/** When a piece of cached data was last synced, keyed by [ge.hackerman.gza.core.data.sync.SyncKey]. */
@Entity(tableName = "sync_state")
internal data class SyncStateEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "synced_at") val syncedAt: Instant,
    /** Routes only: when a screen last asked for it, so app open refreshes what is in use. */
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Instant? = null
)
