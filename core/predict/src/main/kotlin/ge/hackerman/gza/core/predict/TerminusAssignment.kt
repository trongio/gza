package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.model.VehiclePosition
import java.time.Instant
import java.time.ZonedDateTime

/** Live states by row index, and the memory entries of the route's parked buses. */
internal class TerminusOutcome(val states: Map<Int, DepartureState>, val memory: Map<VehicleId, ParkedVehicle>)

/**
 * Gives the buses parked at the stop to the departures of the patterns that start there, for
 * one route. Rows not at a first stop keep their timetable time: v1 has no downstream ETA.
 */
internal class TerminusAssignment(
    private val rules: PredictionRules,
    private val now: ZonedDateTime,
    private val terminus: LatLon,
    private val previous: LayoverMemory
) {
    private val states = HashMap<Int, DepartureState>()
    private val memory = LinkedHashMap<VehicleId, ParkedVehicle>()

    /** [rows] sorted by time; [positions] null when there is no fresh live data. */
    fun assign(routeId: RouteId, rows: List<ScheduledDeparture>, positions: RoutePositions?): TerminusOutcome {
        val firstStopRows = rows.indices.filter { rows[it].atFirstStop }.groupBy { rows[it].pattern }
        if (positions != null && firstStopRows.isNotEmpty()) {
            val parked = positions.vehicles
                .filter { it.isInLayoverAt(terminus, rules) }
                .map { Parked(it, firstSeen(routeId, it.vehicleId)) }
            val byPattern = parked.groupBy { targetPattern(it.position, firstStopRows, rows) }
            firstStopRows.forEach { (pattern, indices) ->
                val queue = byPattern[pattern].orEmpty().sortedWith(QUEUE_ORDER)
                assignPattern(routeId, pattern, queue, indices, rows)
            }
        }
        return TerminusOutcome(states, memory)
    }

    private fun assignPattern(
        routeId: RouteId,
        pattern: PatternSuffix,
        queue: List<Parked>,
        indices: List<Int>,
        rows: List<ScheduledDeparture>
    ) {
        val upcoming = indices.filter { !rows[it].scheduled.isBefore(now) }
        queue.forEach { bus ->
            val index = upcoming.firstOrNull { it !in states }
            if (index != null) {
                val leavesAt = latest(rows[index].scheduled, turnaroundEnd(bus))
                states[index] = DepartureState.Waiting(bus.id, leavesAt)
            }
            memory[bus.id] = ParkedVehicle(routeId, pattern, bus.firstSeen, index?.let { rows[it].scheduled })
        }
        val next = upcoming.firstOrNull()
        if (next != null && next !in states && !rows[next].scheduled.isAfter(now.plus(rules.noBusWindow))) {
            states[next] = DepartureState.NoBusYet
        }
    }

    /**
     * The gateway relabels buses while they wait (TTC_API.md), so a parked bus labelled with a
     * pattern that does not start here counts for the pattern that leaves here next.
     */
    private fun targetPattern(
        bus: VehiclePosition,
        firstStopRows: Map<PatternSuffix, List<Int>>,
        rows: List<ScheduledDeparture>
    ): PatternSuffix {
        if (bus.pattern in firstStopRows) return bus.pattern
        return firstStopRows.minBy { (_, indices) ->
            indices.map { rows[it].scheduled.toInstant() }.firstOrNull { !it.isBefore(now.toInstant()) } ?: Instant.MAX
        }.key
    }

    // A memory entry from the future (the clock moved back) counts as seen now.
    private fun firstSeen(routeId: RouteId, id: VehicleId): Instant {
        val seen = previous.vehicles[id]?.takeIf { it.routeId == routeId }?.firstSeen
        val nowInstant = now.toInstant()
        return if (seen == null || seen.isAfter(nowInstant)) nowInstant else seen
    }

    private fun turnaroundEnd(bus: Parked): ZonedDateTime = bus.firstSeen.plus(rules.minTurnaround).atZone(now.zone)

    private fun latest(a: ZonedDateTime, b: ZonedDateTime): ZonedDateTime = if (b.isAfter(a)) b else a

    private inner class Parked(val position: VehiclePosition, val firstSeen: Instant) {
        val id: VehicleId get() = position.vehicleId
        val waitingFor: Instant get() = previous.vehicles[id]?.waitingFor?.toInstant() ?: Instant.MAX
    }

    private companion object {
        // Buses that already had a departure keep their place, then the longest waiting first.
        val QUEUE_ORDER: Comparator<Parked> =
            compareBy<Parked>({ it.waitingFor }, { it.firstSeen }, { it.id.value })
    }
}
