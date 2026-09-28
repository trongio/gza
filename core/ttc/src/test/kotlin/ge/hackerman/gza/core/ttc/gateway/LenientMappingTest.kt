package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.ttc.gateway.dto.mapEachOrMalformed
import ge.hackerman.gza.core.ttc.gateway.dto.mapEachOrNull
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Test

/** The all-misfit rule after mapping: the same edges as the decode-level rule. */
class LenientMappingTest {
    private val keepEven: (Int) -> Int? = { it.takeIf { n -> n % 2 == 0 } }

    @Test
    fun `nothing sent is empty, never a misfit`() {
        val nothing: List<Int?>? = null
        assertEquals(emptyList(), nothing.mapEachOrNull { _, n -> keepEven(n) })
        assertEquals(emptyList(), emptyList<Int?>().mapEachOrNull { _, n -> keepEven(n) })
        assertEquals(emptyList(), listOf<Int?>(null, null).mapEachOrNull { _, n -> keepEven(n) })
        assertEquals(emptyList(), listOf<Int?>(null).mapEachOrMalformed(keepEven))
    }

    @Test
    fun `some kept drops only the rest`() {
        assertEquals(listOf(2), listOf(1, null, 2, 3).mapEachOrNull { _, n -> keepEven(n) })
        assertEquals(listOf(2), listOf(1, null, 2, 3).mapEachOrMalformed(keepEven))
    }

    @Test
    fun `items sent and none kept is null nested and malformed at the top`() {
        assertNull(listOf(1, null, 3).mapEachOrNull { _, n -> keepEven(n) })
        assertFailsWith<SerializationException> { listOf(1, null, 3).mapEachOrMalformed(keepEven) }
    }

    @Test
    fun `the index is the item's place in what was sent, nulls included`() {
        assertEquals(listOf(0, 2), listOf("a", null, "c").mapEachOrNull { index, _ -> index })
    }
}
