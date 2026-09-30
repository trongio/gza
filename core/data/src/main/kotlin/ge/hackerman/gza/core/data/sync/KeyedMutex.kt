package ge.hackerman.gza.core.data.sync

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One mutex per key: two syncs of the same thing run one after the other (the second then
 * finds fresh data and makes no request), different things run in parallel. Keys are few
 * (catalog, used routes and stops), so the map is never pruned.
 */
internal class KeyedMutex<K : Any> {
    private val mutexes = ConcurrentHashMap<K, Mutex>()

    suspend fun <T> withLock(key: K, block: suspend () -> T): T = mutexes.getOrPut(key) { Mutex() }.withLock { block() }
}
