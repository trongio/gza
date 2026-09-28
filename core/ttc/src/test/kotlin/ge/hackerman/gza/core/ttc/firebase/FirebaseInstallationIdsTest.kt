package ge.hackerman.gza.core.ttc.firebase

import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class FirebaseInstallationIdsTest {
    private val allowed = Regex("[A-Za-z0-9_-]{22}")

    @Test
    fun `ids have the firebase shape`() {
        repeat(1_000) {
            val fid = FirebaseInstallationIds.generate()
            assertEquals(22, fid.length)
            assertTrue(fid.first() in 'c'..'f', fid)
            assertTrue(allowed.matches(fid), fid)
        }
    }

    @Test
    fun `seeded random gives a stable id`() {
        assertEquals(FirebaseInstallationIds.generate(Random(42)), FirebaseInstallationIds.generate(Random(42)))
    }

    @Test
    fun `different ids across calls`() {
        assertEquals(100, List(100) { FirebaseInstallationIds.generate() }.toSet().size)
    }
}
