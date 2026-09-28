package ge.hackerman.gza.core.designsystem.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
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
// Taller than a phone: the specimen of every style does not fit 891dp.
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class TypographyScreenshotTest(
    // Only names the parameterised run in reports.
    @Suppress("unused") theme: String,
    private val darkTheme: Boolean
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typography() = composeRule.captureThemed("typography", darkTheme) { Specimen() }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ThemeParameters.both()
    }
}

private const val EN = "Leave in 7 min"
private const val KA = "გამოდით 7 წუთში"

@Composable
private fun Specimen() {
    val t = MaterialTheme.typography
    val x = GzaTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Line("displayMedium", t.displayMedium, "გზა Gza 326")
        Line("displaySmall", t.displaySmall, "თბილისი Tbilisi")
        Line("headlineMedium", t.headlineMedium, "სახლი Home")
        Line("headlineSmall", t.headlineSmall, "გასვლები Departures")
        Line("titleLarge", t.titleLarge, "თავისუფლების მოედანი")
        Line("titleMedium", t.titleMedium, "$EN / $KA")
        Line("titleSmall", t.titleSmall, "Freedom Square / Площадь Свободы")
        Line("bodyLarge", t.bodyLarge, "$EN / $KA")
        Line("bodyMedium", t.bodyMedium, "ანა პოლიტკოვსკაიას ქუჩა, Ana Politkovskaia St")
        Line("bodySmall", t.bodySmall, "ელოდება ბოლო გაჩერებაზე, გადის 17:13-ზე")
        Line("labelLarge", t.labelLarge, "რეალურ დროში / Live, 3 stops away")
        Line("labelMedium", t.labelMedium, "მხოლოდ განრიგით / Timetable only")
        Line("labelSmall", t.labelSmall, "აგვიანებს 6 წუთით / Running 6 min late")
        Line("timeHero", x.timeHero, "17:13")
        Line("timeLarge", x.timeLarge, "17:13 10:05 00:40 ±4")
        Line("timeMedium", x.timeMedium, "17:13 10:05 00:40 ±4")
        Line("timeSmall", x.timeSmall, "8:12 - 8:58 +2")
        Line("routeNumberLarge", x.routeNumberLarge, "326 551 1 2")
        Line("routeNumber", x.routeNumber, "326 551 1 2")
        Line("routeNumberSmall", x.routeNumberSmall, "326 551 1 2")
    }
}

@Composable
private fun Line(name: String, style: TextStyle, sample: String) {
    Column {
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(sample, style = style, color = MaterialTheme.colorScheme.onBackground)
    }
}
