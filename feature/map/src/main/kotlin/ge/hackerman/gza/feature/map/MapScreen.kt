package ge.hackerman.gza.feature.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.R as DesignR
import ge.hackerman.gza.core.designsystem.component.DepartureRow
import ge.hackerman.gza.core.designsystem.component.DepartureStatus
import ge.hackerman.gza.core.designsystem.component.GzaBanner
import ge.hackerman.gza.core.designsystem.component.GzaBottomSheetScaffold
import ge.hackerman.gza.core.designsystem.component.GzaSheetHeader
import ge.hackerman.gza.core.designsystem.component.TransitMode
import ge.hackerman.gza.core.designsystem.theme.GzaTheme

/**
 * The map with the departures sheet (PLAN 2.7). The canvas is a drawn preview until
 * MapLibre lands (T11); it draws behind the status bar on purpose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.map_preview_description)
    GzaBottomSheetScaffold(
        modifier = modifier.fillMaxSize().testTag(MapTestTags.SCREEN),
        // Handle, title and the first departure: the waiting bus is visible without dragging.
        sheetPeekHeight = 232.dp,
        dragHandleModifier = Modifier.testTag(MapTestTags.SHEET_HANDLE),
        sheetContent = { SheetContent() },
        sheetLabel = stringResource(R.string.map_sheet_label)
    ) {
        MapPreviewCanvas(
            stopColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxSize()
                .testTag(MapTestTags.CANVAS)
                .semantics { contentDescription = description }
        )
    }
}

@Composable
private fun SheetContent() {
    // Scrolls so the whole list is reachable on short screens, in landscape and at large font sizes.
    Column(Modifier.verticalScroll(rememberScrollState()).testTag(MapTestTags.SHEET_CONTENT)) {
        SheetItems()
    }
}

@Composable
private fun SheetItems() {
    GzaSheetHeader(
        title = stringResource(R.string.map_sheet_title, stringResource(DesignR.string.sample_stop_ana_politkovskaia))
    )
    SheetDepartures.forEachIndexed { index, departure ->
        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        DepartureRow(
            routeNumber = departure.route,
            mode = departure.mode,
            destination = stringResource(departure.destination),
            departsAt = departure.departsAt,
            status = departure.status
        )
    }
    GzaBanner(
        text = stringResource(DesignR.string.sample_data_banner),
        modifier = Modifier.padding(16.dp)
    )
}

private class SheetDeparture(
    val route: String,
    val mode: TransitMode,
    val destination: Int,
    val departsAt: String,
    val status: DepartureStatus
)

// The same sample departures as the Now screen; features never depend on each other.
private val SheetDepartures = listOf(
    SheetDeparture(
        "326",
        TransitMode.Bus,
        DesignR.string.sample_headsign_326,
        "17:13",
        DepartureStatus.Waiting("17:13")
    ),
    SheetDeparture("551", TransitMode.Minibus, DesignR.string.sample_headsign_551, "17:24", DepartureStatus.Live(3)),
    SheetDeparture("301", TransitMode.Bus, DesignR.string.sample_headsign_301, "17:31", DepartureStatus.Late(6)),
    SheetDeparture("326", TransitMode.Bus, DesignR.string.sample_headsign_326, "17:49", DepartureStatus.TimetableOnly)
)

@PreviewLightDark
@Composable
private fun MapScreenPreview() {
    GzaTheme {
        Surface { MapScreen() }
    }
}
