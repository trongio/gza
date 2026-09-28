package ge.hackerman.gza.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RouteBadgeScreenshotTest(
    // Only names the parameterised run in reports.
    @Suppress("unused") theme: String,
    private val darkTheme: Boolean
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun routeBadges() = composeRule.captureThemed("route_badges", darkTheme) { Badges() }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ThemeParameters.both()
    }
}

@Composable
private fun Badges() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RouteBadgeSize.entries.forEach { size ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RouteBadge("326", TransitMode.Bus, size = size)
                RouteBadge("551", TransitMode.Minibus, size = size)
                RouteBadge("1", TransitMode.Metro, size = size)
                RouteBadge("2", TransitMode.Metro, size = size)
                RouteBadge("1", TransitMode.CableCar, size = size)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Gateway colour override (lower case), and a malformed one falling back to the bus teal.
            RouteBadge("2", TransitMode.CableCar, color = parseRouteColor("f5861f")!!)
            RouteBadge(
                "37",
                TransitMode.Bus,
                color =
                    parseRouteColor("not-a-colour") ?: defaultRouteColor(TransitMode.Bus, "37")
            )
        }
    }
}
