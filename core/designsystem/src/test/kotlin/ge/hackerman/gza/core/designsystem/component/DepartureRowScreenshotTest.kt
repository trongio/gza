package ge.hackerman.gza.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import ge.hackerman.gza.core.testing.ThemeParameters
import ge.hackerman.gza.core.testing.captureThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Taller than a phone: at 1.5x font size the six rows do not fit 891dp.
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class DepartureRowScreenshotTest(
    // Only names the parameterised run in reports.
    @Suppress("unused") theme: String,
    private val darkTheme: Boolean
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun departureRows() = composeRule.captureThemed("departure_rows", darkTheme) { Rows(English) }

    @Test
    @Config(qualifiers = "+ka")
    fun departureRowsGeorgian() = composeRule.captureThemed("departure_rows_ka", darkTheme) { Rows(Georgian) }

    @Test
    fun departureRowsLargeFont() =
        composeRule.captureThemed("departure_rows_font_scale_150", darkTheme, fontScale = 1.5f) { Rows(English) }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ThemeParameters.both()
    }
}

private class Places(val baratashvili: String, val mall: String, val barbare: String, val long: String)

private val English = Places(
    baratashvili = "Baratashvili St",
    mall = "Tbilisi Mall",
    barbare = "Saint Barbare District",
    long = "Vazha-Pshavela Avenue, Third Microdistrict, next to the Central Children's Hospital"
)

private val Georgian = Places(
    baratashvili = "ბარათაშვილის ქ.",
    mall = "თბილისი მოლი",
    barbare = "წმინდა ბარბარეს უბანი",
    long = "ვაჟა-ფშაველას გამზირი, მესამე მიკრორაიონი, ცენტრალური საბავშვო საავადმყოფოს გვერდით"
)

@Composable
private fun Rows(places: Places) {
    Column {
        DepartureRow(
            "326",
            TransitMode.Bus,
            places.baratashvili,
            "17:13",
            DepartureStatus.Waiting(
                "17:13"
            ),
            leaveBy = "17:08",
            onClick = {
            }
        )
        HorizontalDivider()
        DepartureRow(
            "551",
            TransitMode.Minibus,
            places.mall,
            "17:24",
            DepartureStatus.Live(
                3
            ),
            leaveBy = "17:19",
            onClick = {
            }
        )
        HorizontalDivider()
        DepartureRow("301", TransitMode.Bus, places.barbare, "17:31", DepartureStatus.Late(6), leaveBy = "17:26")
        HorizontalDivider()
        DepartureRow(
            "326",
            TransitMode.Bus,
            places.baratashvili,
            "17:49",
            DepartureStatus.TimetableOnly,
            leaveBy = "17:44"
        )
        HorizontalDivider()
        DepartureRow("1", TransitMode.Metro, places.mall, "17:52", DepartureStatus.Live(null))
        HorizontalDivider()
        DepartureRow(
            "326",
            TransitMode.Bus,
            places.long,
            "18:05",
            DepartureStatus.Live(12),
            leaveBy = "18:00",
            detail = "4 min walk"
        )
    }
}
