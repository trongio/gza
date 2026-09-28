package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.BoundingBox
import ge.hackerman.gza.core.model.GeocodeResult
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternStops
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteDetail
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePolyline
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.model.StopBoard
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TripPlan
import ge.hackerman.gza.core.model.TripRequest
import ge.hackerman.gza.core.ttc.TtcJson
import ge.hackerman.gza.core.ttc.config.GatewayConfigUnavailableException
import ge.hackerman.gza.core.ttc.gateway.dto.BoardArrivalDto
import ge.hackerman.gza.core.ttc.gateway.dto.PatternStopDto
import ge.hackerman.gza.core.ttc.gateway.dto.ProblemDto
import ge.hackerman.gza.core.ttc.gateway.dto.RouteDto
import ge.hackerman.gza.core.ttc.gateway.dto.ServicePeriodDto
import ge.hackerman.gza.core.ttc.gateway.dto.StopDto
import ge.hackerman.gza.core.ttc.gateway.dto.toPolylineDtos
import ge.hackerman.gza.core.ttc.gateway.dto.toPositionDtos
import ge.hackerman.gza.core.ttc.gateway.mapper.toGeocodeResults
import ge.hackerman.gza.core.ttc.gateway.mapper.toPatternStops
import ge.hackerman.gza.core.ttc.gateway.mapper.toRouteDetailOrNull
import ge.hackerman.gza.core.ttc.gateway.mapper.toRoutePolylines
import ge.hackerman.gza.core.ttc.gateway.mapper.toRoutePositions
import ge.hackerman.gza.core.ttc.gateway.mapper.toRouteSchedule
import ge.hackerman.gza.core.ttc.gateway.mapper.toRoutes
import ge.hackerman.gza.core.ttc.gateway.mapper.toStopBoard
import ge.hackerman.gza.core.ttc.gateway.mapper.toStopOrNull
import ge.hackerman.gza.core.ttc.gateway.mapper.toStops
import ge.hackerman.gza.core.ttc.gateway.mapper.toTripPlan
import java.io.IOException
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.ResponseBody
import retrofit2.HttpException

/**
 * Calls enqueue on OkHttp's dispatcher, so the network, the auth interceptor and JSON
 * parsing never run on the caller's thread; only the cheap mapping does. [clock] stamps
 * boards and positions, whose minutes and GPS fixes are relative to when they arrived.
 */
@Suppress("TooManyFunctions") // One function per endpoint, plus the error mapping.
internal class RetrofitTtcGatewayClient(private val service: TtcGatewayService, private val clock: Clock) :
    TtcGatewayClient {
    override suspend fun stops(language: Language): List<Stop> = call {
        service.stops(language.code).decode<List<StopDto?>>().toStops()
    }

    override suspend fun stop(id: StopId, language: Language): Stop = call {
        service.stop(id.value, language.code).toStopOrNull() ?: throw TtcGatewayException.Malformed(null)
    }

    override suspend fun stopRoutes(id: StopId, language: Language): List<Route> = call {
        service.stopRoutes(id.value, language.code).decode<List<RouteDto?>>().toRoutes()
    }

    override suspend fun arrivalBoard(id: StopId, language: Language): StopBoard = call {
        val rows = service.arrivalTimes(id.value, language.code).decode<List<BoardArrivalDto?>>()
        rows.toStopBoard(id, clock.instant())
    }

    override suspend fun routes(language: Language): List<Route> = call {
        service.routes(QueryFormat.ROUTE_MODES, language.code).decode<List<RouteDto?>>().toRoutes()
    }

    override suspend fun route(id: RouteId, language: Language): RouteDetail = call {
        service.route(id.value, language.code).toRouteDetailOrNull() ?: throw TtcGatewayException.Malformed(null)
    }

    override suspend fun schedule(id: RouteId, pattern: PatternSuffix, language: Language): RouteSchedule = call {
        service.schedule(id.value, pattern.value, language.code).decode<List<ServicePeriodDto?>>()
            .toRouteSchedule(id, pattern)
    }

    override suspend fun patternStops(id: RouteId, pattern: PatternSuffix, language: Language): PatternStops = call {
        service.stopsOfPatterns(id.value, pattern.value, language.code).decode<List<PatternStopDto?>>()
            .toPatternStops(id, pattern)
    }

    override suspend fun polylines(id: RouteId, patterns: List<PatternSuffix>): List<RoutePolyline> {
        require(patterns.isNotEmpty()) { "at least one pattern" }
        return call { service.polylines(id.value, QueryFormat.patterns(patterns)).toPolylineDtos().toRoutePolylines() }
    }

    override suspend fun positions(id: RouteId, patterns: List<PatternSuffix>): RoutePositions {
        require(patterns.isNotEmpty()) { "at least one pattern" }
        return call {
            val vehicles = service.positions(id.value, QueryFormat.patterns(patterns)).toPositionDtos()
            vehicles.toRoutePositions(id, clock.instant())
        }
    }

    override suspend fun plan(request: TripRequest, language: Language): TripPlan = call {
        service.plan(QueryFormat.planQuery(request, language)).toTripPlan(request)
    }

    override suspend fun geocode(query: String, language: Language, bounds: BoundingBox): List<GeocodeResult> =
        call { service.geocode(query, language.code, QueryFormat.bbox(bounds)).toGeocodeResults() }

    override suspend fun reverseGeocode(at: LatLon, language: Language): List<GeocodeResult> = call {
        service.reverseGeocode(QueryFormat.coordinate(at.lat), QueryFormat.coordinate(at.lon), language.code)
            .toGeocodeResults()
    }

    private inline fun <reified T> JsonElement.decode(): T = TtcJson.decodeFromJsonElement(this)

    /**
     * Every failure leaves as a [TtcGatewayException], except cancellation, which must reach
     * the coroutine machinery untouched.
     */
    // HttpException is deliberately not kept as a cause: it holds the whole response, whose
    // request carries the key header. Only the code and the problem survive.
    @Suppress("SwallowedException")
    private inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        throw TtcGatewayException.Http(e.code(), e.response()?.errorBody()?.let(::problemOrNull))
    } catch (e: GatewayConfigUnavailableException) {
        throw TtcGatewayException.NoKey(e)
    } catch (e: IOException) {
        throw TtcGatewayException.Network(e)
    } catch (e: IllegalArgumentException) {
        // kotlinx.serialization's SerializationException is an IllegalArgumentException.
        throw TtcGatewayException.Malformed(e)
    } catch (e: KotlinNullPointerException) {
        // Retrofit's answer to a 204 or null body for a non-null return type.
        throw TtcGatewayException.Malformed(e)
    }

    /** Only RFC 7807 JSON is read, capped; anything else (the 500 text, 401 `Unauthorized`) is null. */
    @Suppress("SwallowedException") // An unreadable problem only loses the detail; the status still surfaces.
    private fun problemOrNull(body: ResponseBody): GatewayProblem? = body.use {
        if (it.contentType()?.subtype?.contains("json", ignoreCase = true) != true) return@use null
        try {
            // Okio, not InputStream.readNBytes: that is API 33, and minSdk is 26.
            val source = it.source()
            source.request(MAX_PROBLEM_BYTES)
            val text = source.buffer.readUtf8(minOf(source.buffer.size, MAX_PROBLEM_BYTES))
            val problem = TtcJson.decodeFromString<ProblemDto>(text)
            GatewayProblem(problem.title, problem.detail)
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val MAX_PROBLEM_BYTES = 4L * 1024
    }
}
