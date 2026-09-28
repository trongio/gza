package ge.hackerman.gza.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.core.testing.ThemeParameters
import ge.hackerman.gza.core.testing.captureThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Banner, top app bar and bottom sheet scaffold: the chrome around every screen. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ChromeScreenshotTest(
    // Only names the parameterised run in reports.
    @Suppress("unused") theme: String,
    private val darkTheme: Boolean
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun banners() = composeRule.captureThemed("banners", darkTheme) {
        Column(Modifier.width(379.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GzaBanner("Sample data. Live departures arrive in a later update.")
            GzaBanner("სანიმუშო მონაცემები. რეალური გასვლები შემდეგ განახლებაში გამოჩნდება.")
            GzaBanner("Offline. Showing the timetable from 17:02.", tone = BannerTone.Offline)
        }
    }

    @Test
    fun topAppBar() = composeRule.captureThemed("top_app_bar", darkTheme) {
        Column(Modifier.width(379.dp)) {
            GzaTopAppBar(title = "Settings")
            HorizontalDivider()
            GzaTopAppBar(title = "Home", subtitle = "Ana Politkovskaia Street, 4 min walk")
            HorizontalDivider()
            GzaTopAppBar(title = "სახლი", subtitle = "ანა პოლიტკოვსკაიას ქუჩა, 4 წთ ფეხით")
        }
    }

    @Test
    fun bottomSheetScaffold() = composeRule.captureThemed("bottom_sheet_scaffold", darkTheme) {
        Box(Modifier.size(width = 379.dp, height = 480.dp)) {
            GzaBottomSheetScaffold(
                sheetPeekHeight = 260.dp,
                sheetContent = {
                    GzaSheetHeader("Departures from Freedom Square", subtitle = "2 routes")
                    DepartureRow("326", TransitMode.Bus, "Baratashvili St", "17:13", DepartureStatus.Waiting("17:13"))
                    DepartureRow("551", TransitMode.Minibus, "Tbilisi Mall", "17:24", DepartureStatus.Live(3))
                }
            ) { Box(Modifier.fillMaxSize().background(GzaTheme.colors.mapLand)) }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ThemeParameters.both()
    }
}
