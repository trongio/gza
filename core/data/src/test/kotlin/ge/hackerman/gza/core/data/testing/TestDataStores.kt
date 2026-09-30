package ge.hackerman.gza.core.data.testing

import androidx.datastore.core.DataStore
import ge.hackerman.gza.core.data.datastore.DataStoreFiles
import ge.hackerman.gza.core.data.datastore.TtcConfigData
import ge.hackerman.gza.core.data.datastore.UserPreferencesData
import ge.hackerman.gza.core.data.di.jsonDataStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking

/**
 * Real DataStores on files, each with its own scope, so a test can close one and open a new
 * instance on the same file (two live instances on one file throw).
 */
internal class TestDataStores(private val dir: File) : AutoCloseable {
    private val scopes = mutableListOf<CoroutineScope>()

    fun file(name: String): File = File(dir, "datastore/$name")

    fun ttcConfig(name: String = DataStoreFiles.TTC_CONFIG): DataStore<TtcConfigData> =
        jsonDataStore(TtcConfigData.serializer(), TtcConfigData(), newScope()) { file(name) }

    fun userPreferences(): DataStore<UserPreferencesData> = jsonDataStore(
        UserPreferencesData.serializer(),
        UserPreferencesData(),
        newScope()
    ) { file(DataStoreFiles.USER_PREFERENCES) }

    fun newScope(): CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }

    /** Closes every store opened so far, waiting until each has let go of its file. */
    fun closeAll() {
        scopes.forEach { scope ->
            val job = scope.coroutineContext.job
            scope.cancel()
            runBlocking { job.join() }
        }
        scopes.clear()
    }

    override fun close() = closeAll()
}
