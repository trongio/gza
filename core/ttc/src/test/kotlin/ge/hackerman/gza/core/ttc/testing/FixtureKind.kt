package ge.hackerman.gza.core.ttc.testing

/** Which endpoint a fixture file is a response of, from its folder and name. */
enum class FixtureKind {
    STOPS,
    STOP,
    ROUTE_LIST,
    ROUTE_DETAIL,
    BOARD,
    SCHEDULE,
    STOPS_OF_PATTERNS,
    POLYLINES,
    POSITIONS,
    PLAN,
    GEOCODE,
    PROBLEM,
    TEXT;

    companion object {
        fun of(path: String): FixtureKind {
            val folder = path.substringBefore('/')
            val name = path.substringAfterLast('/')
            return when {
                !path.endsWith(".json") -> TEXT
                folder == "terminus" -> ofTerminus(name)
                folder == "errors" -> if (name.startsWith("positions")) POSITIONS else PROBLEM
                else -> BY_FOLDER.getValue(folder)
            }
        }

        private fun ofTerminus(name: String): FixtureKind = when {
            name.startsWith("arrival-times") -> BOARD
            name.startsWith("positions") -> POSITIONS
            name.startsWith("schedule") -> SCHEDULE
            else -> error("unknown terminus fixture $name")
        }

        private val BY_FOLDER = mapOf(
            "stops" to STOPS,
            "stop" to STOP,
            "stop-routes" to ROUTE_LIST,
            "routes" to ROUTE_LIST,
            "route" to ROUTE_DETAIL,
            "arrival-times" to BOARD,
            "schedule" to SCHEDULE,
            "stops-of-patterns" to STOPS_OF_PATTERNS,
            "polylines" to POLYLINES,
            "positions" to POSITIONS,
            "plan" to PLAN,
            "geocode" to GEOCODE,
            "reverse-geocode" to GEOCODE
        )
    }
}
