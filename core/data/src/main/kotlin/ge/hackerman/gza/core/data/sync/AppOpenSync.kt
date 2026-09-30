package ge.hackerman.gza.core.data.sync

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ge.hackerman.gza.core.data.coroutines.ApplicationScope
import ge.hackerman.gza.core.data.database.GzaDatabase
import ge.hackerman.gza.core.data.language.ContentLanguage
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the app does each time it comes to the foreground (register it on
 * `ProcessLifecycleOwner`). Every step is a no-op when nothing is stale, so no throttle: keeps
 * the weekly catalog job scheduled, asks for the catalog now when it is missing or overdue,
 * refreshes the routes in use, and re-reads the app language.
 */
@Singleton
@Suppress("LongParameterList") // Injected collaborators, one per step.
class AppOpenSync @Inject internal constructor(
    private val scheduler: SyncScheduler,
    private val routeSync: RouteSync,
    private val database: GzaDatabase,
    private val contentLanguage: ContentLanguage,
    private val policy: StalenessPolicy,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        scope.launch { run() }
    }

    internal suspend fun run() {
        step("Language refresh") { contentLanguage.refresh() }
        step("Periodic catalog sync") { scheduler.ensurePeriodicCatalogSync() }
        step("Catalog check") { if (isCatalogOverdue()) scheduler.requestCatalogSyncNow() }
        // In the app process, not WorkManager: it is "on app open" by definition, one indexed
        // query when nothing is stale, and offline it just fails until the next open.
        step("Active routes refresh") { routeSync.refreshActiveRoutes() }
    }

    // The weekly job normally keeps this fresh; a day past a week means it is late or failing.
    private suspend fun isCatalogOverdue(): Boolean {
        val overdueAfter = policy.catalogMaxAge + OVERDUE_GRACE
        val now = clock.instant()
        val syncState = database.syncStateDao()
        return database.stopDao().count() == 0 ||
            database.routeDao().count() == 0 ||
            policy.isStale(syncState.get(SyncKey.Stops.value)?.syncedAt, overdueAfter, now) ||
            policy.isStale(syncState.get(SyncKey.Routes.value)?.syncedAt, overdueAfter, now)
    }

    // One failed step must not skip the others or reach the process's crash handler.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun step(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what failed: ${e.javaClass.name}")
        }
    }

    private companion object {
        const val TAG = "GzaAppOpenSync"
        val OVERDUE_GRACE: Duration = Duration.ofDays(1)
    }
}
