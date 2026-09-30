package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.ttc.http.TtcGateway
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures
import ge.hackerman.gza.core.ttc.testing.FixtureGateway
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class TtcGatewayServiceTest {
    private val gateway = FixtureGateway()

    @AfterEach
    fun tearDown() = gateway.close()

    @Test
    fun `every service method is valid for Retrofit`() {
        // validateEagerly parses every annotation at creation and throws on the first bad one.
        gateway.client()
    }

    @Test
    fun `requests land under the configured base with the key`() {
        gateway.serve("stop/1-970-en.json")
        runBlocking { gateway.client().stop(StopId("1:970"), Language.EN) }
        val request = gateway.single()
        assertEquals("/pis-gateway/api/v2/stops/1:970?locale=en", request.target)
        assertEquals(FirebaseFixtures.GATEWAY_KEY, request.headers[TtcGateway.API_KEY_HEADER])
        assertEquals(gateway.server.url("/").host, request.url.host)
    }
}
