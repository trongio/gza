package ge.hackerman.gza.core.data.testing

import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.PatternStops
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteDetail
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePolyline
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.ttc.config.ConfigSource
import ge.hackerman.gza.core.ttc.config.GatewayConfig
import ge.hackerman.gza.core.ttc.config.GatewayConfigProvider
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayClient
import ge.hackerman.gza.core.ttc.gateway.TtcGatewayClientFactory
import ge.hackerman.gza.core.ttc.http.GatewayAuthInterceptor
import ge.hackerman.gza.core.ttc.http.TtcHttpClients
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient

/**
 * Domain objects parsed from the real gateway fixtures of `:core:ttc` by the real client, so
 * the data layer is tested on what the gateway really sends. Each object is parsed once per
 * test JVM (the stops list is 440 KB).
 */
internal object FixtureDomain {
    const val STOP_970 = "1:970"

    /** Route short names of the recorded routes to their ids. */
    val routeIds: Map<String, RouteId> = mapOf(
        "301" to RouteId("1:R29981"),
        "326" to RouteId("1:R97493"),
        "551" to RouteId("1:minibusR24579"),
        "472" to RouteId("1:minibusR25521")
    )
    private val namesById = routeIds.entries.associate { (name, id) -> id.value to name }

    fun routeId(name: String): RouteId = routeIds.getValue(name)

    private val cache = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> cached(key: String, load: suspend TtcGatewayClient.() -> T): T =
        cache.getOrPut(key) { runBlocking { client.load() } } as T

    fun stops(language: Language): List<Stop> = cached("stops-$language") { stops(language) }

    fun routes(language: Language): List<Route> = cached("routes-$language") { routes(language) }

    fun stopRoutes(id: StopId): List<Route> = cached("stop-routes-${id.value}") { stopRoutes(id, Language.EN) }

    fun route(id: RouteId, language: Language): RouteDetail = cached("route-${id.value}-$language") {
        route(id, language)
    }

    fun patternStops(id: RouteId, pattern: PatternSuffix): PatternStops =
        cached("pattern-stops-${id.value}-${pattern.value}") { patternStops(id, pattern, Language.EN) }

    fun schedule(id: RouteId, pattern: PatternSuffix): RouteSchedule =
        cached("schedule-${id.value}-${pattern.value}") { schedule(id, pattern, Language.EN) }

    fun polylines(id: RouteId): List<RoutePolyline> =
        cached("polylines-${id.value}") { polylines(id, route(id, Language.EN).patterns.map { it.suffix }) }

    fun suffixes(id: RouteId): List<PatternSuffix> = route(id, Language.EN).patterns.map { it.suffix }

    private val client: TtcGatewayClient by lazy {
        val server = MockWebServer()
        server.dispatcher = FixtureDispatcher()
        server.start()
        val provider = object : GatewayConfigProvider {
            private val config = GatewayConfig(server.url("/pis-gateway"), SENTINEL_KEY, ConfigSource.REMOTE, null, 1)

            override fun peek(): GatewayConfig = config

            override suspend fun current(): GatewayConfig = config

            override suspend fun refreshAfterRejection(rejected: GatewayConfig): GatewayConfig? = null
        }
        val http = TtcHttpClients.gatewayClient(OkHttpClient(), GatewayAuthInterceptor(provider), null)
        TtcGatewayClientFactory.create(http, MutableClock(), Dispatchers.Unconfined)
    }

    private class FixtureDispatcher : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val fixture = fixtureFor(request)
            val body = fixture?.let { FixtureDomain::class.java.getResource("/fixtures/$it")?.readText() }
            return if (body == null) {
                MockResponse.Builder().code(NOT_FOUND).build()
            } else {
                MockResponse.Builder().code(OK).addHeader("Content-Type", "application/json").body(body).build()
            }
        }

        private fun fixtureFor(request: RecordedRequest): String? {
            val url = request.url
            val locale = url.queryParameter("locale")
            val segments = url.pathSegments.dropWhile { it != "api" }.drop(1)
            val name = segments.getOrNull(2)?.let { namesById[it] }
            val safe = { raw: String? -> raw?.replace(':', '-') }
            return when {
                segments == listOf("v2", "stops") -> "stops/all-$locale.json"

                segments == listOf("v3", "routes") -> "routes/all-$locale.json"

                segments.size == 4 && segments[1] == "stops" && segments[3] == "routes" ->
                    "stop-routes/${safe(segments[2])}-$locale.json"

                name == null -> null

                segments.size == 3 -> "route/$name-$locale.json"

                segments[3] == "schedule" -> "schedule/$name-${safe(url.queryParameter("patternSuffix"))}-$locale.json"

                segments[3] == "stops-of-patterns" ->
                    "stops-of-patterns/$name-${safe(url.queryParameter("patternSuffixes"))}-$locale.json"

                segments[3] == "polylines" -> "polylines/$name.json"

                else -> null
            }
        }
    }

    private const val SENTINEL_KEY = "gza-data-test-sentinel-key"
    private const val OK = 200
    private const val NOT_FOUND = 404
}
