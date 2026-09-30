package ge.hackerman.gza.core.data.datastore

import androidx.datastore.core.CorruptionException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test

class JsonDataStoreSerializerTest {
    @Serializable
    data class Sample(val name: String = "default", val count: Int = 1)

    private val serializer = JsonDataStoreSerializer(Sample.serializer(), Sample())

    private suspend fun read(text: String): Sample = serializer.readFrom(ByteArrayInputStream(text.encodeToByteArray()))

    @Test
    fun `unknown fields are ignored`() = runTest {
        assertEquals(Sample("a", 2), read("""{"name":"a","count":2,"added":"later"}"""))
    }

    @Test
    fun `missing fields take their defaults`() = runTest {
        assertEquals(Sample("a", 1), read("""{"name":"a"}"""))
        assertEquals(Sample(), read("{}"))
    }

    @Test
    fun `garbage is a corruption, and the message quotes none of it`() = runTest {
        listOf("not json", "", "[1,2]", """{"count":"many"}""", "\u0000\u0001").forEach { text ->
            val error = assertFailsWith<CorruptionException>(text) { read(text) }
            assertFalse("many" in error.message.orEmpty())
        }
    }

    @Test
    fun `writes then reads back, defaults included`() = runTest {
        val out = ByteArrayOutputStream()
        serializer.writeTo(Sample(), out)
        assertEquals("""{"name":"default","count":1}""", out.toString(Charsets.UTF_8))
        assertEquals(Sample(), read(out.toString(Charsets.UTF_8)))
    }
}
