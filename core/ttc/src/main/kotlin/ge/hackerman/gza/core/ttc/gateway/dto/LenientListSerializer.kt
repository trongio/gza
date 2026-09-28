package ge.hackerman.gza.core.ttc.gateway.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull

/**
 * A list inside a DTO, decoded element by element like the top-level lists: one mistyped
 * pattern, stop or step becomes null in its place instead of failing its parent. The place is
 * kept because some mappers read the index (a schedule stop's fallback position).
 *
 * The all-misfit rule holds here too: a list with non-null elements none of which fit throws,
 * so the parent item is dropped by the list above it, and a response whose items all go that
 * way is Malformed. Lists whose meaning is their position (coordinates, the legs of an
 * itinerary) are not lenient: a hole there changes what the rest says.
 */
internal abstract class LenientListSerializer<T : Any>(private val element: KSerializer<T>) : KSerializer<List<T?>> {
    private val strict = ListSerializer(element.nullable)

    override val descriptor: SerialDescriptor = strict.descriptor

    override fun deserialize(decoder: Decoder): List<T?> {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Only JSON is supported")
        val body = input.decodeJsonElement()
        val array = body as? JsonArray ?: throw SerializationException("Expected a JSON array, got ${shapeOf(body)}")
        val decoded = array.map { it.decodeOrNull(element, input.json) }
        requireSomeFit(array, decoded.count { it != null })
        return decoded
    }

    override fun serialize(encoder: Encoder, value: List<T?>) = strict.serialize(encoder, value)
}

internal object LenientPatterns : LenientListSerializer<PatternDto>(PatternDto.serializer())

internal object LenientScheduledStops : LenientListSerializer<ScheduledStopDto>(ScheduledStopDto.serializer())

internal object LenientStops : LenientListSerializer<StopDto>(StopDto.serializer())

internal object LenientSteps : LenientListSerializer<StepDto>(StepDto.serializer())

internal object LenientStrings : LenientListSerializer<String>(String.serializer())

/** Throws when [array] has non-null elements and none of them decoded. */
internal fun requireSomeFit(array: JsonArray, decodedCount: Int) {
    if (decodedCount == 0) {
        val present = array.count { it !is JsonNull }
        if (present > 0) throw SerializationException("None of $present elements fit the expected shape")
    }
}
