package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures
import ge.hackerman.gza.core.ttc.testing.Fixtures
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class FixtureHygieneTest {
    @ParameterizedTest
    @MethodSource("fixtures")
    fun `every fixture has a sidecar that says where it came from`(path: String) {
        val meta = Fixtures.meta(path)
        assertTrue(meta.request.startsWith("/v"), "request is an api path: ${meta.request}")
        assertFalse("://" in meta.request, "no host in ${meta.request}")
        assertEquals(isDerivedPath(path), meta.derived, "derived flag of $path")
        if (meta.derived) {
            assertFalse(meta.derivedFrom.isNullOrEmpty(), "derivedFrom of $path")
            meta.derivedFrom?.forEach { assertTrue(Fixtures.exists(it), "derived from missing $it") }
            assertFalse(meta.reason.isNullOrBlank(), "reason of $path")
        }
    }

    @Test
    fun `no sidecar without its fixture`() {
        val fixtures = Fixtures.all().map(Fixtures::metaPath).toSet()
        Fixtures.allFiles().filter(Fixtures::isSidecar).forEach { assertTrue(it in fixtures, "orphan $it") }
    }

    @ParameterizedTest
    @MethodSource("allFiles")
    fun `no fixture carries a key`(path: String) {
        val text = Fixtures.text(path)
        FORBIDDEN.forEach { assertFalse(it in text, "$path contains a forbidden marker") }
    }

    @Test
    fun `the real layover capture is there`() {
        assertNotNull(Fixtures.text("terminus/551-20260928T2043/arrival-times.json"))
    }

    companion object {
        private val FORBIDDEN = listOf("x-api-key", "PIS_GATEWAY_KEY", "AIza", FirebaseFixtures.GATEWAY_KEY)

        fun isDerivedPath(path: String): Boolean = "derived-" in path

        @JvmStatic
        fun fixtures(): List<String> = Fixtures.all()

        @JvmStatic
        fun allFiles(): List<String> = Fixtures.allFiles()
    }
}
