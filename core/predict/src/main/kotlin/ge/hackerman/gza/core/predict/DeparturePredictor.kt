package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.BoardArrival
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.StopBoard
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.model.VehicleId
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Departures from one stop for yesterday's late trips, today and tomorrow, from the timetable
 * plus live positions (PLAN.md 2.0).
 *
 * A bus parked between trips at the first stop of its pattern is [DepartureState.Waiting] and
 * leaves at its timetable time or after a minimum turnaround, whichever is later: never
 * "arriving now", whatever the board says. The board is attached as a hint and never changes
 * a time. Deterministic for a given request and [clock].
 */
class DeparturePredictor(private val clock: Clock, private val rules: PredictionRules = PredictionRules.Default) {
    fun predict(request: StopRequest): StopPrediction {
        val now = clock.nowInTbilisi()
        val today = now.toLocalDate()
        val dates = listOf(today.minusDays(1), today, today.plusDays(1))
        val stopId = request.stop.id

        val missing = mutableListOf<RouteId>()
        val departures = mutableListOf<PredictedDeparture>()
        val memory = LinkedHashMap<VehicleId, ParkedVehicle>()
        val liveRoutes = mutableSetOf<RouteId>()

        for (snapshot in request.routes) {
            if (snapshot.schedules.none { it.servesStop(stopId) }) {
                missing += snapshot.route.id
                continue
            }
            val rows = snapshot.schedules.flatMap { it.departuresAt(stopId, dates) }.sortedBy { it.scheduled }
            val positions = snapshot.positions?.takeIf { it.isFresh(now) }
            val outcome = TerminusAssignment(rules, now, request.stop.location, request.memory)
                .assign(snapshot.route.id, rows, positions)
            if (positions != null) {
                liveRoutes += snapshot.route.id
                memory += outcome.memory
            }
            departures += rows.mapIndexed { index, row ->
                val state = outcome.states[index] ?: DepartureState.TimetableOnly
                snapshot.toDeparture(row, state)
            }.withBoardHints(request.board, snapshot, now)
        }

        // An offline poll must not erase what earlier polls learned about a route.
        request.memory.vehicles.forEach { (id, parked) ->
            if (parked.routeId !in liveRoutes && id !in memory) memory[id] = parked
        }

        val earliest = now.minus(rules.pastGrace)
        return StopPrediction(
            stopId = stopId,
            now = now,
            departures = departures.filter { !it.predicted.isBefore(earliest) }.sortedWith(DEPARTURE_ORDER),
            missingTimetables = missing,
            memory = LayoverMemory(memory)
        )
    }

    // A small negative age is clock skew between the phone and the gateway, and counts as fresh.
    private fun RoutePositions.isFresh(now: ZonedDateTime): Boolean {
        val age = Duration.between(fetchedAt, now.toInstant())
        return age <= rules.maxPositionsAge && age >= rules.maxClockSkew.negated()
    }

    private fun RouteSnapshot.toDeparture(row: ScheduledDeparture, state: DepartureState) = PredictedDeparture(
        routeId = route.id,
        routeShortName = route.shortName,
        kind = route.kind,
        pattern = row.pattern,
        headsign = patterns.firstOrNull { it.suffix == row.pattern }?.headsign,
        stopId = row.stopId,
        serviceDate = row.serviceDate,
        scheduled = row.scheduled,
        predicted = when (state) {
            is DepartureState.Waiting -> state.leavesAt
            is DepartureState.Late -> state.leavesAt
            DepartureState.NoBusYet, DepartureState.TimetableOnly -> row.scheduled
        },
        state = state
    )

    /**
     * The board row of this route and pattern goes on the pattern's next row; a row with no
     * pattern cannot say which pattern it counts, so it goes on the route's single next row.
     * It is a hint for the UI only: at a terminus it reads 0 for a bus that is waiting, not
     * arriving.
     */
    private fun List<PredictedDeparture>.withBoardHints(
        board: StopBoard?,
        snapshot: RouteSnapshot,
        now: ZonedDateTime
    ): List<PredictedDeparture> {
        if (board == null) return this
        val route = snapshot.route
        val matching = board.arrivals.filter {
            it.routeShortName == route.shortName && sameBoardKind(it.kind, route.kind)
        }
        // Rows are sorted by time, so the first upcoming row of each group is its next one.
        val upcoming = indices.filter { !this[it].scheduled.isBefore(now) }
        val hints = HashMap<Int, BoardArrival>()
        upcoming.groupBy { this[it].pattern }.forEach { (pattern, rows) ->
            matching.firstOrNull { it.pattern == pattern }?.let { hints[rows.first()] = it }
        }
        val next = upcoming.firstOrNull()
        if (next != null && next !in hints) {
            matching.firstOrNull { it.pattern == null }?.let { hints[next] = it }
        }
        return mapIndexed { index, row ->
            hints[index]?.let {
                row.copy(boardHint = BoardHint(it.realtimeMinutesHint, it.scheduledMinutesHint, board.fetchedAt))
            } ?: row
        }
    }

    // Metro and cable car numbers restart at 1, so a board "1" must match the kind too. Buses
    // and minibuses both say BUS on the board and have unique numbers.
    private fun sameBoardKind(boardKind: TransportKind, routeKind: TransportKind): Boolean = when (routeKind) {
        TransportKind.METRO, TransportKind.CABLE_CAR -> boardKind == routeKind
        else -> boardKind != TransportKind.METRO && boardKind != TransportKind.CABLE_CAR
    }

    private companion object {
        val DEPARTURE_ORDER: Comparator<PredictedDeparture> = compareBy<PredictedDeparture>(
            { it.predicted.toInstant() },
            { it.scheduled.toInstant() },
            { it.routeShortName.length },
            { it.routeShortName },
            { it.pattern.value }
        )
    }
}
