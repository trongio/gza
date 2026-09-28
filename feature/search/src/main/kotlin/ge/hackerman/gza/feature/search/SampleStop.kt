package ge.hackerman.gza.feature.search

import androidx.annotation.StringRes
import ge.hackerman.gza.core.designsystem.R as DesignR
import ge.hackerman.gza.core.designsystem.component.TransitMode

/** Fixed sample stops until search is real (T08). */
internal data class SampleStop(@StringRes val name: Int, val code: String?, val routes: List<Pair<String, TransitMode>>)

internal val SampleStops = listOf(
    SampleStop(
        DesignR.string.sample_stop_ana_politkovskaia,
        "970",
        listOf("301" to TransitMode.Bus, "326" to TransitMode.Bus, "551" to TransitMode.Minibus)
    ),
    SampleStop(DesignR.string.sample_stop_freedom_square, "3639", listOf("326" to TransitMode.Bus)),
    SampleStop(DesignR.string.sample_stop_liberty_square_metro, null, listOf("1" to TransitMode.Metro))
)
