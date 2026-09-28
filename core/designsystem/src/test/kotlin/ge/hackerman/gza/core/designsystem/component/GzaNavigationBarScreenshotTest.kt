package ge.hackerman.gza.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
class GzaNavigationBarScreenshotTest(
    // Only names the parameterised run in reports.
    @Suppress("unused") theme: String,
    private val darkTheme: Boolean
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun navigationBar() = composeRule.captureThemed("navigation_bar", darkTheme) { Bars() }

    @Test
    fun navigationBarLargeFont() =
        composeRule.captureThemed("navigation_bar_font_scale_150", darkTheme, fontScale = 1.5f) { Bars() }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ThemeParameters.both()
    }
}

@Composable
private fun Bars() {
    Column(Modifier.width(379.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SampleNavigationBar(selected = 0)
        // The longest Georgian labels, to prove they fit.
        SampleNavigationBar(selected = 3, labels = GeorgianLabels)
    }
}

internal val GeorgianLabels = listOf("ახლა", "ძიება", "რუკა", "დაგეგმვა", "პარამეტრები")
