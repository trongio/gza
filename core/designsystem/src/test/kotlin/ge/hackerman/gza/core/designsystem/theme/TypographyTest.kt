package ge.hackerman.gza.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TypographyTest {
    private val t = GzaTypography
    private val extended = GzaExtendedTypography()

    @Test
    fun headlinesUseTheDisplayFace() {
        listOf(t.displayLarge, t.displaySmall, t.headlineMedium, t.headlineSmall, t.titleLarge).forEach {
            assertEquals(GzaDisplayFamily, it.fontFamily)
        }
    }

    @Test
    fun bodyTitlesAndLabelsUseTheBodyFace() {
        listOf(t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall, t.labelLarge, t.labelSmall)
            .forEach { assertEquals(GzaBodyFamily, it.fontFamily) }
    }

    @Test
    fun timesUseTheMonoFace() {
        listOf(extended.timeHero, extended.timeLarge, extended.timeMedium, extended.timeSmall).forEach {
            assertEquals(GzaMonoFamily, it.fontFamily)
        }
    }

    @Test
    fun routeNumbersUseTheDisplayFace() {
        listOf(extended.routeNumber, extended.routeNumberLarge, extended.routeNumberSmall).forEach {
            assertEquals(GzaDisplayFamily, it.fontFamily)
        }
    }

    @Test
    fun everySizeIsInSpSoItFollowsTheSystemFontSize() {
        val all: List<TextStyle> = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
            extended.timeHero, extended.timeLarge, extended.timeMedium, extended.timeSmall,
            extended.routeNumber, extended.routeNumberLarge, extended.routeNumberSmall
        )
        all.forEach {
            assertTrue(it.fontSize.isSp, "fontSize ${it.fontSize}")
            assertTrue(it.lineHeight.isSp, "lineHeight ${it.lineHeight}")
        }
    }
}
