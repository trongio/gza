package ge.hackerman.gza.core.data.database.dao

import ge.hackerman.gza.core.data.database.GzaDatabase
import ge.hackerman.gza.core.data.database.entity.RouteEntity
import ge.hackerman.gza.core.data.database.entity.StopEntity
import ge.hackerman.gza.core.data.database.entity.SyncStateEntity
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.model.TransportKind
import java.time.Instant
import org.junit.After
import org.junit.Before

/** An in-memory database per test, built like the app's. */
internal abstract class DaoTest {
    protected lateinit var db: GzaDatabase

    @Before
    fun openDatabase() {
        db = TestDatabase.inMemory()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    protected fun stop(
        id: String,
        en: String? = "Stop $id",
        ka: String? = null,
        code: String? = id.substringAfter(':')
    ) = StopEntity(id, code, en, ka, lat = 41.7, lon = 44.7, kind = TransportKind.BUS)

    protected fun route(
        id: String,
        short: String = id.substringAfter(':'),
        en: String? = "Route $short",
        ka: String? = null
    ) = RouteEntity(id, short, en, ka, color = 0x00B38B, kind = TransportKind.BUS)

    protected fun synced(key: String, at: Instant = T0) = SyncStateEntity(key, at)

    companion object {
        val T0: Instant = Instant.parse("2026-09-28T13:11:00Z")
    }
}
