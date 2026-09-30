package ge.hackerman.gza.core.data.database.dao

import ge.hackerman.gza.core.data.database.entity.SyncStateEntity

/**
 * Rows and their sync time read in one transaction. Two separate flows would each re-query
 * after a sync commits, and a screen could briefly see the new rows with the old sync time.
 */
internal data class TableSnapshot<T>(val rows: List<T>, val syncState: SyncStateEntity?)
