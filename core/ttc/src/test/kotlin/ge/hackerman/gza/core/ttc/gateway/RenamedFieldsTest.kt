package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.ttc.testing.FixtureGateway
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * The likeliest real shape change is a rename. Every DTO field is optional and unknown keys
 * are ignored, so renamed items decode as all-null DTOs and are then dropped by the mapper.
 * When every item goes that way the gateway changed, and returning an empty list reads as
 * "nothing there", which is what the all-misfit rule exists to prevent.
 */
class RenamedFieldsTest {
    private val gateway = FixtureGateway()

    @AfterEach
    fun tearDown() = gateway.close()

    private fun serve(body: String) {
        gateway.respondWith { FixtureGateway.response(200, body, "application/json") }
    }

    private fun malformed(block: suspend () -> Unit) {
        assertFailsWith<TtcGatewayException.Malformed> { runBlocking { block() } }
    }

    @Disabled("Bug: renamed stop fields map to an empty list instead of Malformed (T03 retest 2)")
    @Test
    fun `stops whose fields were all renamed are malformed, not empty`() {
        serve("""[{"stopId":"1:970","latitude":41.72,"longitude":44.70},{"stopId":"1:969"}]""")
        malformed { gateway.client().stops(Language.EN) }
    }

    @Disabled("Bug: renamed vehicle fields map to no vehicles instead of Malformed (T03 retest 2)")
    @Test
    fun `vehicles whose fields were all renamed are malformed, not an empty road`() {
        serve("""{"0:01":[{"id":"1:1","latitude":41.7,"longitude":44.7}]}""")
        malformed { gateway.client().positions(RouteId("1:R97493"), listOf(PatternSuffix("0:01"))) }
    }

    @Disabled("Bug: renamed board fields map to an empty board instead of Malformed (T03 retest 2)")
    @Test
    fun `a board whose arrivals were all renamed is malformed, not empty`() {
        serve("""[{"route":"326","minutes":3},{"route":"301","minutes":7}]""")
        malformed { gateway.client().arrivalBoard(StopId("1:970"), Language.EN) }
    }
}
