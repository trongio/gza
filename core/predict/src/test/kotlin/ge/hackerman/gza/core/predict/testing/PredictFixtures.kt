package ge.hackerman.gza.core.predict.testing

import ge.hackerman.gza.core.model.BoardArrival
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.Pattern
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteColor
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.ScheduledStop
import ge.hackerman.gza.core.model.ServiceMinute
import ge.hackerman.gza.core.model.ServicePeriod
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.model.StopBoard
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.StopRef
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.model.VehiclePosition
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the real gateway captures of `:core:ttc` (shared as a test resource dir) into model
 * types. The T03 mappers are internal to `:core:ttc`, so this maps by hand. Strict on purpose:
 * the fixtures are known good, and a missing field should fail the test, not be papered over.
 */
object PredictFixtures {
    private const val ROOT = "/fixtures"

    val ROUTE_301 = RouteId("1:R29981")
    val ROUTE_326 = RouteId("1:R97493")
    val ROUTE_551 = RouteId("1:minibusR24579")
    val ROUTE_472 = RouteId("1:minibusR25521")

    fun text(path: String): String =
        checkNotNull(javaClass.getResource("$ROOT/$path")) { "missing fixture $path" }.readText()

    fun json(path: String): JsonElement = Json.parseToJsonElement(text(path))

    /** The `recordedAt` of the sidecar `.meta.json` next to [path]. */
    fun recordedAt(path: String): Instant {
        val meta = path.removeSuffix(".json") + ".meta.json"
        return Instant.parse(json(meta).jsonObject.string("recordedAt"))
    }

    fun schedule(path: String, routeId: RouteId, pattern: String): RouteSchedule = RouteSchedule(
        routeId = routeId,
        pattern = PatternSuffix(pattern),
        periods = json(path).jsonArray.map { it.jsonObject.toPeriod() }
    )

    fun positions(path: String, routeId: RouteId): RoutePositions = RoutePositions(
        routeId = routeId,
        fetchedAt = recordedAt(path),
        vehicles = json(path).jsonObject.flatMap { (pattern, vehicles) ->
            vehicles.jsonArray.map { it.jsonObject.toVehicle(PatternSuffix(pattern)) }
        }
    )

    fun board(path: String, stopId: StopId): StopBoard = StopBoard(
        stopId = stopId,
        fetchedAt = recordedAt(path),
        arrivals = json(path).jsonArray.map { it.jsonObject.toBoardArrival() }
    )

    fun stop(path: String): Stop = json(path).jsonObject.toStop()

    fun patternStops(path: String): List<Stop> = json(path).jsonArray.map { it.jsonObject.obj("stop").toStop() }

    fun routeDetailPatterns(path: String): List<Pattern> = json(path).jsonObject.getValue("patterns").jsonArray.map {
        val pattern = it.jsonObject
        Pattern(
            suffix = PatternSuffix(pattern.string("patternSuffix")),
            directionId = pattern.getValue("directionId").jsonPrimitive.int,
            firstStop = pattern.obj("firstStop").toStopRef(),
            lastStop = pattern.obj("lastStop").toStopRef(),
            headsign = pattern.string("headsign")
        )
    }

    fun route(path: String): Route {
        val route = json(path).jsonObject
        val id = RouteId(route.string("id"))
        return Route(
            id = id,
            shortName = route.string("shortName"),
            longName = null,
            color = RouteColor.ofHexOrNull(route.string("color")),
            kind = if (id.isMinibus) TransportKind.MINIBUS else kind(route.string("mode"))
        )
    }

    private fun JsonObject.toPeriod() = ServicePeriod(
        fromDay = DayOfWeek.valueOf(string("fromDay")),
        toDay = DayOfWeek.valueOf(string("toDay")),
        serviceDates = getValue("serviceDates").jsonArray.map { LocalDate.parse(it.jsonPrimitive.content) }.toSet(),
        stops = getValue("stops").jsonArray.map { element ->
            val stop = element.jsonObject
            ScheduledStop(
                stopId = StopId(stop.string("id")),
                name = stop.string("name"),
                position = stop.getValue("position").jsonPrimitive.int,
                times = stop.string("arrivalTimes").split(',').map { checkNotNull(ServiceMinute.parseOrNull(it)) }
            )
        }
    )

    private fun JsonObject.toVehicle(pattern: PatternSuffix) = VehiclePosition(
        vehicleId = VehicleId(string("vehicleId")),
        pattern = pattern,
        location = LatLon(double("lat"), double("lon")),
        headingDegrees = nullable("heading")?.jsonPrimitive?.content?.toDouble(),
        nextStopId = nullable("nextStopId")?.jsonPrimitive?.content?.let(::StopId)
    )

    private fun JsonObject.toBoardArrival() = BoardArrival(
        routeShortName = string("shortName"),
        pattern = PatternSuffix.ofOrNull(nullable("patternSuffix")?.jsonPrimitive?.content),
        headsign = nullable("headsign")?.jsonPrimitive?.content,
        color = RouteColor.ofHexOrNull(nullable("color")?.jsonPrimitive?.content),
        kind = kind(string("vehicleMode")),
        realtime = getValue("realtime").jsonPrimitive.content.toBooleanStrict(),
        realtimeMinutesHint = nullable("realtimeArrivalMinutes")?.jsonPrimitive?.int,
        scheduledMinutesHint = nullable("scheduledArrivalMinutes")?.jsonPrimitive?.int
    )

    private fun JsonObject.toStop() = Stop(
        id = StopId(string("id")),
        code = nullable("code")?.jsonPrimitive?.content,
        name = string("name"),
        location = LatLon(double("lat"), double("lon")),
        kind = kind(string("vehicleMode"))
    )

    private fun JsonObject.toStopRef() = StopRef(StopId(string("id")), nullable("name")?.jsonPrimitive?.content)

    private fun kind(mode: String): TransportKind = when (mode) {
        "BUS" -> TransportKind.BUS
        "SUBWAY" -> TransportKind.METRO
        "GONDOLA" -> TransportKind.CABLE_CAR
        else -> TransportKind.UNKNOWN
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.double(key: String): Double = getValue(key).jsonPrimitive.content.toDouble()

    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

    private fun JsonObject.nullable(key: String): JsonElement? = get(key)?.takeUnless { it is JsonNull }
}
