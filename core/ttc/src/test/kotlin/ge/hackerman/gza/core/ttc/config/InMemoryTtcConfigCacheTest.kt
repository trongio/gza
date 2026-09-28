package ge.hackerman.gza.core.ttc.config

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class InMemoryTtcConfigCacheTest {
    private val cache = InMemoryTtcConfigCache()
    private val config = CachedGatewayConfig("https://transit.ttc.com.ge/pis-gateway", "k", Instant.EPOCH)
    private val installation = FirebaseInstallation("fid", "refresh", "auth", Instant.EPOCH)

    @Test
    fun `empty cache reads null`() = runTest {
        assertNull(cache.readConfig())
        assertNull(cache.readInstallation())
    }

    @Test
    fun `config round trips`() = runTest {
        cache.writeConfig(config)
        assertEquals(config, cache.readConfig())
    }

    @Test
    fun `installation round trips and null clears it`() = runTest {
        cache.writeInstallation(installation)
        assertEquals(installation, cache.readInstallation())
        cache.writeInstallation(null)
        assertNull(cache.readInstallation())
    }
}
