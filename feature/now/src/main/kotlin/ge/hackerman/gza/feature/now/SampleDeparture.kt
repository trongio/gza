package ge.hackerman.gza.feature.now

import androidx.annotation.StringRes
import ge.hackerman.gza.core.designsystem.R as DesignR
import ge.hackerman.gza.core.designsystem.component.DepartureStatus
import ge.hackerman.gza.core.designsystem.component.TransitMode

/** Fixed sample departures from stop 1:970, so screenshots are deterministic. Removed in T07. */
internal data class SampleDeparture(
    val route: String,
    val mode: TransitMode,
    @StringRes val destination: Int,
    val departsAt: String,
    val leaveBy: String,
    val status: DepartureStatus
)

internal val SampleDepartures = listOf(
    // The parked 326 the official board calls "0 min": waiting, leaving at 17:13.
    SampleDeparture(
        "326",
        TransitMode.Bus,
        DesignR.string.sample_headsign_326,
        "17:13",
        "17:08",
        DepartureStatus.Waiting("17:13")
    ),
    SampleDeparture(
        "551",
        TransitMode.Minibus,
        DesignR.string.sample_headsign_551,
        "17:24",
        "17:19",
        DepartureStatus.Live(3)
    ),
    SampleDeparture(
        "301",
        TransitMode.Bus,
        DesignR.string.sample_headsign_301,
        "17:31",
        "17:26",
        DepartureStatus.Late(6)
    ),
    SampleDeparture(
        "326",
        TransitMode.Bus,
        DesignR.string.sample_headsign_326,
        "17:49",
        "17:44",
        DepartureStatus.TimetableOnly
    )
)
