package ge.hackerman.gza.core.data.datastore

import ge.hackerman.gza.core.data.testing.TestDataStores
import ge.hackerman.gza.core.ttc.config.CachedGatewayConfig
import ge.hackerman.gza.core.ttc.config.FirebaseInstallation
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DataStoreTtcConfigCacheTest {
    @TempDir
    lateinit var dir: File

    private val stores by lazy { TestDataStores(dir) }
    private val config =
        CachedGatewayConfig("https://example.com/gw", "sentinel-gateway-key", Instant.ofEpochSecond(1_000))
    private val installation =
        FirebaseInstallation("fid-1", "sentinel-refresh", "sentinel-auth", Instant.ofEpochSecond(2_000))

    @AfterEach
    fun tearDown() = stores.closeAll()

    private fun cache() = DataStoreTtcConfigCache(stores.ttcConfig())

    @Test
    fun `an empty store reads nothing`() = runBlocking {
        val cache = cache()
        assertNull(cache.readConfig())
        assertNull(cache.readInstallation())
    }

    @Test
    fun `config and installation round trip across a restart`() = runBlocking {
        cache().run {
            writeConfig(config)
            writeInstallation(installation)
        }
        stores.closeAll()
        val reopened = cache()
        assertEquals(config, reopened.readConfig())
        assertEquals(installation, reopened.readInstallation())
    }

    @Test
    fun `writing null clears only the installation`() = runBlocking {
        val cache = cache()
        cache.writeConfig(config)
        cache.writeInstallation(installation)
        cache.writeInstallation(null)
        assertNull(cache.readInstallation())
        assertEquals(config, cache.readConfig())
    }

    @Test
    fun `config and installation writes never overwrite each other`() = runBlocking {
        val cache = cache()
        cache.writeInstallation(installation)
        cache.writeConfig(config)
        assertEquals(installation, cache.readInstallation())
        val newer = installation.copy(authToken = "sentinel-auth-2")
        cache.writeInstallation(newer)
        assertEquals(config, cache.readConfig())
        assertEquals(newer, cache.readInstallation())
    }

    @Test
    fun `a corrupt file reads as empty and is replaced on the next write`() = runBlocking {
        val file = stores.file(DataStoreFiles.TTC_CONFIG)
        file.parentFile.mkdirs()
        file.writeText("{ this is not json")
        val cache = cache()
        assertNull(cache.readConfig())
        cache.writeConfig(config)
        assertEquals(config, cache.readConfig())
    }

    @Test
    fun `records never print a key or token`() {
        val texts = listOf(
            ConfigRecord("https://example.com", "sentinel-gateway-key", 1).toString(),
            InstallationRecord("fid-1", "sentinel-refresh", "sentinel-auth", 1).toString(),
            TtcConfigData(
                ConfigRecord("https://example.com", "sentinel-gateway-key", 1),
                InstallationRecord("fid-1", "sentinel-refresh", "sentinel-auth", 1)
            ).toString()
        )
        texts.forEach { text ->
            listOf("sentinel-gateway-key", "sentinel-refresh", "sentinel-auth").forEach {
                assertFalse(it in text, "$text leaks $it")
            }
        }
        assertTrue("fid-1" in texts[1])
    }

    @Test
    fun `the file is the one named in DataStoreFiles`() = runBlocking {
        cache().writeConfig(config)
        assertTrue(stores.file(DataStoreFiles.TTC_CONFIG).isFile)
        assertEquals("ttc_config.json", DataStoreFiles.TTC_CONFIG)
    }
}
