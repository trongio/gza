package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.VehicleId
import ge.hackerman.gza.core.model.VehiclePosition
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/** Live states by row index, and the memory entries of the route's parked buses. */
internal class TerminusOutcome(val states: Map<Int, DepartureState>, val memory: Map<ParkedKey, ParkedVehicle>)

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
    private val memory = LinkedHashMap<ParkedKey, ParkedVehicle>()

    /** [rows] sorted by time; [positions] null when there is no fresh live data. */
    fun assign(routeId: RouteId, rows: List<ScheduledDeparture>, positions: RoutePositions?): TerminusOutcome {
        val firstStopRows = rows.indices.filter { rows[it].atFirstStop }.groupBy { rows[it].pattern }
        if (positions != null && firstStopRows.isNotEmpty()) {
            val parked = positions.vehicles
                .filter { it.isInLayoverAt(terminus, rules) }
                .map { vehicle ->
                    val remembered = previous.vehicles[ParkedKey(routeId, vehicle.vehicleId)]
                    Parked(vehicle, remembered, firstSeen(remembered))
                }
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
        val notLate = queue.filterNot { bus -> claimLate(routeId, pattern, bus, indices, rows) }
        notLate.forEach { bus ->
            val index = upcoming.firstOrNull { it !in states }
            if (index != null) {
                val leavesAt = latest(rows[index].scheduled, turnaroundEnd(bus))
                states[index] = DepartureState.Waiting(bus.id, leavesAt)
            }
            memory[ParkedKey(routeId, bus.id)] =
                ParkedVehicle(pattern, bus.firstSeen, index?.let { rows[it].scheduled })
        }
        val next = upcoming.firstOrNull()
        if (next != null && next !in states && !rows[next].scheduled.isAfter(now.plus(rules.noBusWindow))) {
            states[next] = DepartureState.NoBusYet
        }
    }

    /**
     * A bus still parked after the departure it was waiting for keeps that departure, now
     * late, and leaves a step after now on each poll (PLAN.md 2.0). Past [PredictionRules.lateLookback]
     * whole minutes the trip is treated as dropped and the bus queues for the next one.
     */
    private fun claimLate(
        routeId: RouteId,
        pattern: PatternSuffix,
        bus: Parked,
        indices: List<Int>,
        rows: List<ScheduledDeparture>
    ): Boolean {
        val index = missedRow(bus, indices, rows) ?: return false
        val missed = rows[index].scheduled
        val leavesAt = latest(now.plus(rules.lateStep), turnaroundEnd(bus))
        states[index] = DepartureState.Late(bus.id, leavesAt, Duration.between(missed, leavesAt))
        memory[ParkedKey(routeId, bus.id)] = ParkedVehicle(pattern, bus.firstSeen, missed)
        return true
    }

    /** The unclaimed, recently passed row this bus was waiting for, if any. */
    private fun missedRow(bus: Parked, indices: List<Int>, rows: List<ScheduledDeparture>): Int? {
        val missed = bus.remembered?.waitingFor
            ?.takeIf { it.isBefore(now) }
            ?.takeIf { Duration.between(it, now).toMinutes() <= rules.lateLookback.toMinutes() }
        return missed?.let { indices.firstOrNull { it !in states && rows[it].scheduled.isEqual(missed) } }
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
    private fun firstSeen(remembered: ParkedVehicle?): Instant {
        val seen = remembered?.firstSeen
        val nowInstant = now.toInstant()
        return if (seen == null || seen.isAfter(nowInstant)) nowInstant else seen
    }

    private fun turnaroundEnd(bus: Parked): ZonedDateTime = bus.firstSeen.plus(rules.minTurnaround).atZone(now.zone)

    private fun latest(a: ZonedDateTime, b: ZonedDateTime): ZonedDateTime = if (b.isAfter(a)) b else a

    private class Parked(val position: VehiclePosition, val remembered: ParkedVehicle?, val firstSeen: Instant) {
        val id: VehicleId get() = position.vehicleId
        val waitingFor: Instant get() = remembered?.waitingFor?.toInstant() ?: Instant.MAX
    }

    private companion object {
        // Buses that already had a departure keep their place, then the longest waiting first.
        val QUEUE_ORDER: Comparator<Parked> =
            compareBy<Parked>({ it.waitingFor }, { it.firstSeen }, { it.id.value })
    }
}
