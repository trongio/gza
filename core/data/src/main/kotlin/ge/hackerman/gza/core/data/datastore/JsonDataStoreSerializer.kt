package ge.hackerman.gza.core.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A typed DataStore file as JSON. Unknown keys are ignored and missing ones take their
 * defaults, so a file written by an older or newer app version still reads. Anything that
 * is not the expected JSON is a [CorruptionException], which the store's corruption handler
 * turns into [defaultValue].
 */
internal class JsonDataStoreSerializer<T>(private val serializer: KSerializer<T>, override val defaultValue: T) :
    Serializer<T> {
    // The cause is dropped on purpose: kotlinx.serialization messages quote the input, and
    // the config file holds the gateway key.
    @Suppress("SwallowedException")
    override suspend fun readFrom(input: InputStream): T {
        val text = input.readBytes().decodeToString()
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable ${serializer.descriptor.serialName}: ${e.javaClass.simpleName}")
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Invalid ${serializer.descriptor.serialName}: ${e.javaClass.simpleName}")
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
