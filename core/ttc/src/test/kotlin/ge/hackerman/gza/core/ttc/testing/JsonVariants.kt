package ge.hackerman.gza.core.ttc.testing

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/** A step into JSON: an object key or an array index. */
sealed interface JsonStep {
    data class Key(val name: String) : JsonStep

    data class Index(val index: Int) : JsonStep
}

typealias JsonPath = List<JsonStep>

fun JsonPath.render(): String = joinToString("") {
    when (it) {
        is JsonStep.Key -> ".${it.name}"
        is JsonStep.Index -> "[${it.index}]"
    }
}

/** Mutations of real responses for the lenient parsing tests. */
object JsonVariants {
    val UNKNOWN_FIELD: JsonObject = buildJsonObject {
        put(
            "x",
            buildJsonArray {
                add(JsonPrimitive(1))
                add(JsonNull)
            }
        )
    }
    const val UNKNOWN_KEY = "zzUnknown"

    fun trimArrays(element: JsonElement, max: Int): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.take(max).map { trimArrays(it, max) })
        is JsonObject -> JsonObject(element.mapValues { trimArrays(it.value, max) })
        is JsonPrimitive -> element
    }

    fun addUnknownEverywhere(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map(::addUnknownEverywhere))

        is JsonObject -> JsonObject(
            element.mapValues {
                addUnknownEverywhere(it.value)
            } + (UNKNOWN_KEY to UNKNOWN_FIELD)
        )

        is JsonPrimitive -> element
    }

    /** Paths of every object field, recursively. */
    fun fieldPaths(element: JsonElement, prefix: JsonPath = emptyList()): List<JsonPath> = when (element) {
        is JsonArray -> element.flatMapIndexed { i, child -> fieldPaths(child, prefix + JsonStep.Index(i)) }

        is JsonObject -> element.flatMap { (key, child) ->
            val path = prefix + JsonStep.Key(key)
            listOf(path) + fieldPaths(child, path)
        }

        is JsonPrimitive -> emptyList()
    }

    /** Paths of every array, the root included. */
    fun arrayPaths(element: JsonElement, prefix: JsonPath = emptyList()): List<JsonPath> = when (element) {
        is JsonArray -> listOf(prefix) +
            element.flatMapIndexed { i, child -> arrayPaths(child, prefix + JsonStep.Index(i)) }

        is JsonObject -> element.flatMap { (key, child) -> arrayPaths(child, prefix + JsonStep.Key(key)) }

        is JsonPrimitive -> emptyList()
    }

    /** Paths of string fields whose key is in [keys]. */
    fun stringFieldPaths(element: JsonElement, keys: Set<String>): List<JsonPath> = fieldPaths(element).filter { path ->
        val last = path.last()
        last is JsonStep.Key && last.name in keys && (get(element, path) as? JsonPrimitive)?.isString == true
    }

    fun get(element: JsonElement, path: JsonPath): JsonElement? = path.fold(element as JsonElement?) { current, step ->
        when (step) {
            is JsonStep.Key -> (current as? JsonObject)?.get(step.name)
            is JsonStep.Index -> (current as? JsonArray)?.getOrNull(step.index)
        }
    }

    fun set(element: JsonElement, path: JsonPath, value: JsonElement): JsonElement = update(element, path) { value }

    fun remove(element: JsonElement, path: JsonPath): JsonElement {
        val parent = path.dropLast(1)
        val key = (path.last() as JsonStep.Key).name
        return update(element, parent) { JsonObject((it as JsonObject) - key) }
    }

    fun insertNull(element: JsonElement, arrayPath: JsonPath): JsonElement =
        update(element, arrayPath) { JsonArray(listOf(JsonNull) + (it as JsonArray)) }

    private fun update(element: JsonElement, path: JsonPath, change: (JsonElement) -> JsonElement): JsonElement {
        if (path.isEmpty()) return change(element)
        val rest = path.drop(1)
        return when (val step = path.first()) {
            is JsonStep.Key -> {
                val obj = element as JsonObject
                JsonObject(obj + (step.name to update(obj.getValue(step.name), rest, change)))
            }

            is JsonStep.Index -> {
                val array = element as JsonArray
                JsonArray(array.mapIndexed { i, child -> if (i == step.index) update(child, rest, change) else child })
            }
        }
    }
}
