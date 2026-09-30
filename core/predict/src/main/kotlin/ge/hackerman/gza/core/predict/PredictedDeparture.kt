package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.Pattern
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.Route
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.model.StopBoard
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.model.VehicleId
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** Everything the engine needs for one stop. The caller builds it from its repositories. */
data class StopRequest(
    /** The stop asked about; its location is the terminus whose layover matters. */
    val stop: Stop,
    /** Routes serving the stop that the user wants. */
    val routes: List<RouteSnapshot>,
    /** A hint only: attached to rows, never used to compute a time. */
    val board: StopBoard? = null,
    /** What the previous [DeparturePredictor.predict] for this stop returned. */
    val memory: LayoverMemory = LayoverMemory.Empty
)

data class RouteSnapshot(
    /** Id, short name (joins the board) and kind. */
    val route: Route,
    /** One per pattern the caller has; empty when the timetable is missing. */
    val schedules: List<RouteSchedule>,
    /** From the route detail, only for headsigns. */
    val patterns: List<Pattern> = emptyList(),
    /** Null when there is no live data (offline, error, not polled). */
    val positions: RoutePositions? = null
)

data class StopPrediction(
    val stopId: StopId,
    /** The instant the prediction was made for, in Tbilisi time. */
    val now: ZonedDateTime,
    /** Sorted by predicted time, then scheduled time, then route number, then pattern. */
    val departures: List<PredictedDeparture>,
    /** Routes with no timetable for this stop at all; T07 says "timetable unavailable". */
    val missingTimetables: List<RouteId>,
    /** Feed into the next [DeparturePredictor.predict] for this stop. */
    val memory: LayoverMemory
)

data class PredictedDeparture(
    val routeId: RouteId,
    val routeShortName: String,
    val kind: TransportKind,
    val pattern: PatternSuffix,
    val headsign: String?,
    val stopId: StopId,
    /** The service day the time is listed under: `24:05` keeps the earlier date. */
    val serviceDate: LocalDate,
    /** Timetable time, Tbilisi. */
    val scheduled: ZonedDateTime,
    /** What the UI counts down to. */
    val predicted: ZonedDateTime,
    val state: DepartureState,
    val confidence: Confidence = Confidence.NotRated,
    val boardHint: BoardHint? = null
)

sealed interface DepartureState {
    /** Parked between trips at this pattern's first stop; leaves at [leavesAt], never earlier. */
    data class Waiting(val vehicleId: VehicleId, val leavesAt: ZonedDateTime) : DepartureState

    /** Was waiting for this departure, its time passed and it is still parked. */
    data class Late(val vehicleId: VehicleId, val leavesAt: ZonedDateTime, val lateBy: Duration) : DepartureState

    /** First stop, live data fine, departure soon and no bus there yet. */
    data object NoBusYet : DepartureState

    /** Timetable time only: a downstream stop, a later trip, or no live data. */
    data object TimetableOnly : DepartureState
}

/** Placeholder until T10 rates predictions. */
sealed interface Confidence {
    data object NotRated : Confidence
}

/** The board row for this route and pattern, as sent. Never used to compute a time. */
data class BoardHint(val realtimeMinutes: Int?, val scheduledMinutes: Int?, val observedAt: Instant)
