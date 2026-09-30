package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.VehicleId
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
    private val previous: LayoverMemory,
    /** Every vehicle id listed on any live route of this request, parked or not. */
    private val listedLive: Set<VehicleId>
) {
    private val nowInstant = now.toInstant()
    private val states = HashMap<Int, DepartureState>()
    private val memory = LinkedHashMap<ParkedKey, ParkedVehicle>()

    /** [rows] sorted by time; [positions] null when there is no fresh live data. */
    fun assign(routeId: RouteId, rows: List<ScheduledDeparture>, positions: RoutePositions?): TerminusOutcome {
        if (positions == null) return TerminusOutcome(states, memory)
        val remembered = previous.vehicles
            .filter { (key, entry) -> key.routeId == routeId && !entry.isStale() }
            .mapKeys { (key, _) -> key.vehicleId }
        val firstStopRows = rows.indices.filter { rows[it].atFirstStop }.groupBy { rows[it].pattern }
        val listed = positions.vehicles.mapTo(HashSet()) { it.vehicleId }
        val inLayover = positions.vehicles.filter { it.isInLayoverAt(terminus, rules) }
        if (firstStopRows.isNotEmpty()) {
            val parked = inLayover
                // The feed can list one bus under two patterns: it is one bus and takes one
                // row, counted for the pattern that starts here when one of them does.
                .sortedBy { it.pattern !in firstStopRows }
                .distinctBy { it.vehicleId }
                .map { vehicle ->
                    val entry = remembered[vehicle.vehicleId]
                    val firstSeen = entry?.firstSeen?.coerceAtMost(nowInstant) ?: nowInstant
                    Parked(vehicle.vehicleId, vehicle.pattern, entry, firstSeen, lastSeen = nowInstant)
                }
            val held = heldThroughDropout(remembered, listed + listedLive)
            val byPattern = (parked + held).groupBy { targetPattern(it, firstStopRows, rows) }
            firstStopRows.forEach { (pattern, indices) ->
                val queue = byPattern[pattern].orEmpty().sortedWith(QUEUE_ORDER)
                assignPattern(routeId, pattern, queue, indices, rows)
            }
        }
        keepThroughDropout(routeId, remembered, seenMoving = listed - inLayover.mapTo(HashSet()) { it.vehicleId })
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
            remember(routeId, pattern, bus, index?.let { rows[it].scheduled })
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
        val missed = bus.remembered?.waitingFor?.takeIf { it.isRecentlyPast() }
        val index = missed?.let { time -> indices.firstOrNull { it !in states && rows[it].scheduled.isEqual(time) } }
            ?: return false
        val leavesAt = latest(now.plus(rules.lateStep), turnaroundEnd(bus))
        states[index] = DepartureState.Late(bus.id, leavesAt, Duration.between(missed, leavesAt))
        remember(routeId, pattern, bus, missed)
        return true
    }

    private fun remember(routeId: RouteId, pattern: PatternSuffix, bus: Parked, waitingFor: ZonedDateTime?) {
        memory[ParkedKey(routeId, bus.id)] = ParkedVehicle(pattern, bus.firstSeen, waitingFor, bus.lastSeen)
    }

    /**
     * The gateway sometimes leaves a parked bus out of one poll. A remembered bus missing from
     * the feed within [PredictionRules.memoryDropout] of when it was last seen is still parked:
     * it keeps its place in the queue, so its row does not flicker to "no bus" or vanish for
     * one poll. GPS wins over memory: a bus in the feed but not parked here has left, and
     * neither it nor its entry is held again until it is seen parked here; an id listed on
     * any live route of the request is somewhere the GPS can see, so it is not held either.
     */
    private fun heldThroughDropout(remembered: Map<VehicleId, ParkedVehicle>, listed: Set<VehicleId>): List<Parked> =
        remembered.filter { (vehicleId, entry) ->
            vehicleId !in listed && !entry.seenMoving && entry.withinDropout(nowInstant, rules)
        }
            .map { (vehicleId, entry) ->
                Parked(vehicleId, null, entry, entry.firstSeen.coerceAtMost(nowInstant), entry.lastSeen)
            }

    /**
     * Entries not given a row this poll (no first-stop rows, or the bus was seen moving) are
     * still kept through the dropout, so the bus is still late, and still holds its first
     * sighting, when it comes back parked. An entry of a bus [seenMoving] on this route is
     * marked, so a later poll that leaves the bus out does not hold its row again.
     */
    private fun keepThroughDropout(
        routeId: RouteId,
        remembered: Map<VehicleId, ParkedVehicle>,
        seenMoving: Set<VehicleId>
    ) {
        remembered.forEach { (vehicleId, entry) ->
            val key = ParkedKey(routeId, vehicleId)
            if (key !in memory && entry.withinDropout(nowInstant, rules)) {
                memory[key] = if (vehicleId in seenMoving) entry.copy(seenMoving = true) else entry
            }
        }
    }

    /**
     * An old entry (from before a long gap, or yesterday) says nothing about the bus parked
     * now, which may have pulled in seconds ago: it is a new sighting, with a new turnaround.
     */
    private fun ParkedVehicle.isStale(): Boolean {
        val missedLongAgo = waitingFor?.let { it.isBefore(now) && !it.isRecentlyPast() } ?: false
        return missedLongAgo || Duration.between(lastSeen, nowInstant) > rules.memoryMaxAge
    }

    /** Before now by at most [PredictionRules.lateLookback] whole minutes. */
    private fun ZonedDateTime.isRecentlyPast(): Boolean =
        isBefore(now) && Duration.between(this, now).toMinutes() <= rules.lateLookback.toMinutes()

    /**
     * The gateway relabels buses while they wait (TTC_API.md), so a parked bus labelled with a
     * pattern that does not start here keeps the pattern it was counted for on earlier polls,
     * and failing that counts for the pattern that leaves here next.
     */
    private fun targetPattern(
        bus: Parked,
        firstStopRows: Map<PatternSuffix, List<Int>>,
        rows: List<ScheduledDeparture>
    ): PatternSuffix {
        val known = listOfNotNull(bus.label, bus.remembered?.pattern).firstOrNull { it in firstStopRows }
        return known ?: firstStopRows.minBy { (_, indices) ->
            indices.map { rows[it].scheduled.toInstant() }.firstOrNull { !it.isBefore(nowInstant) } ?: Instant.MAX
        }.key
    }

    private fun turnaroundEnd(bus: Parked): ZonedDateTime = bus.firstSeen.plus(rules.minTurnaround).atZone(now.zone)

    /**
     * [remembered] is null for a bus seen for the first time or whose entry is stale. [label]
     * is null for a bus held through a dropout, whose [lastSeen] stays the poll that last saw
     * it, so the dropout window does not stretch.
     */
    private class Parked(
        val id: VehicleId,
        val label: PatternSuffix?,
        val remembered: ParkedVehicle?,
        val firstSeen: Instant,
        val lastSeen: Instant
    ) {
        val waitingFor: Instant get() = remembered?.waitingFor?.toInstant() ?: Instant.MAX
    }

    private companion object {
        // Buses that already had a departure keep their place, then the longest waiting first.
        val QUEUE_ORDER: Comparator<Parked> =
            compareBy<Parked>({ it.waitingFor }, { it.firstSeen }, { it.id.value })
    }
}

private fun latest(a: ZonedDateTime, b: ZonedDateTime): ZonedDateTime = if (b.isAfter(a)) b else a

// A lastSeen after now (the clock moved back) says nothing about how long ago the bus was seen.
private fun ParkedVehicle.withinDropout(now: Instant, rules: PredictionRules): Boolean {
    val gap = Duration.between(lastSeen, now)
    return !gap.isNegative && gap <= rules.memoryDropout
}
