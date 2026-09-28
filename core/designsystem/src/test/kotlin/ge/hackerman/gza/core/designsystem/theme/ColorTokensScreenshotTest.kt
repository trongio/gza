package ge.hackerman.gza.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.component.badgeContentColor
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
class ColorTokensScreenshotTest(
    // Only names the parameterised run in reports.
    @Suppress("unused") theme: String,
    private val darkTheme: Boolean
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun colorTokens() = composeRule.captureThemed("color_tokens", darkTheme) { Swatches() }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ThemeParameters.both()
    }
}

@Composable
private fun Swatches() {
    val scheme = MaterialTheme.colorScheme
    val gza = GzaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Swatch("primary", scheme.primary, scheme.onPrimary)
        Swatch("primaryContainer", scheme.primaryContainer, scheme.onPrimaryContainer)
        Swatch("secondaryContainer", scheme.secondaryContainer, scheme.onSecondaryContainer)
        Swatch("tertiary", scheme.tertiary, scheme.onTertiary)
        Swatch("surface", scheme.surface, scheme.onSurface, outline = scheme.outlineVariant)
        Swatch("surfaceContainer", scheme.surfaceContainer, scheme.onSurfaceVariant)
        Swatch("surfaceContainerHighest", scheme.surfaceContainerHighest, scheme.onSurface)
        Swatch("errorContainer", scheme.errorContainer, scheme.onErrorContainer)
        Swatch("waiting", gza.waitingContainer, gza.onWaitingContainer, outline = gza.waitingOutline)
        Swatch("live", gza.liveContainer, gza.onLiveContainer, dot = gza.liveIndicator)
        Swatch("late", gza.lateContainer, gza.onLateContainer)
        Swatch("timetable", Color.Transparent, gza.onTimetable, outline = gza.timetableOutline)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Swatch("land", gza.mapLand, scheme.onSurface, Modifier.weight(1f))
            Swatch("street", gza.mapStreet, scheme.onSurface, Modifier.weight(1f))
            Swatch("water", gza.mapWater, scheme.onSurface, Modifier.weight(1f))
        }
        listOf(
            "Bus" to TransitColors.Bus,
            "Minibus" to TransitColors.Minibus,
            "Metro 1" to TransitColors.MetroLine1,
            "Metro 2" to TransitColors.MetroLine2,
            "Cable car" to TransitColors.CableCar
        ).forEach { (name, color) -> Swatch(name, color, badgeContentColor(color)) }
    }
}

@Composable
private fun Swatch(
    name: String,
    color: Color,
    onColor: Color,
    modifier: Modifier = Modifier,
    outline: Color = Color.Transparent,
    dot: Color? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(color, MaterialTheme.shapes.small)
            .border(1.dp, outline, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot != null) {
            Box(Modifier.width(8.dp).height(8.dp).background(dot, MaterialTheme.shapes.extraLarge))
            Box(Modifier.width(8.dp))
        }
        Text(name, color = onColor, style = MaterialTheme.typography.labelLarge)
    }
}
