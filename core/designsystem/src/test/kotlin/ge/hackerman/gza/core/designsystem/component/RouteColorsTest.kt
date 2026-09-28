package ge.hackerman.gza.core.designsystem.component

import androidx.compose.ui.graphics.Color
import ge.hackerman.gza.core.designsystem.theme.TransitColors
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

class RouteColorsTest {
    @Test
    fun parsesGatewayColoursWithOrWithoutHashInAnyCase() {
        assertEquals(TransitColors.Bus, parseRouteColor("00B38B"))
        assertEquals(TransitColors.CableCar, parseRouteColor("f5861f"))
        assertEquals(TransitColors.Minibus, parseRouteColor("#0033B4"))
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "12345", "GGGGGG", "00B38B00", "#", "00B38"])
    fun malformedColoursAreNull(hex: String?) {
        assertNull(parseRouteColor(hex))
    }

    @Test
    fun defaultColourPerMode() {
        assertEquals(TransitColors.Bus, defaultRouteColor(TransitMode.Bus, "326"))
        assertEquals(TransitColors.Minibus, defaultRouteColor(TransitMode.Minibus, "551"))
        assertEquals(TransitColors.CableCar, defaultRouteColor(TransitMode.CableCar, "1"))
    }

    @Test
    fun metroLineColourFollowsTheLineNumber() {
        assertEquals(TransitColors.MetroLine1, defaultRouteColor(TransitMode.Metro, "1"))
        assertEquals(TransitColors.MetroLine2, defaultRouteColor(TransitMode.Metro, "2"))
        assertEquals(TransitColors.MetroLine1, defaultRouteColor(TransitMode.Metro, "3"))
    }

    @Test
    fun everyTransitColourGetsReadableBadgeText() {
        TransitColors.all.forEach { background ->
            val ratio = contrastRatio(background, badgeContentColor(background))
            assertTrue(ratio >= 4.5f, "contrast $ratio on $background")
        }
    }

    @Test
    fun badgeTextIsInkOnLightColoursAndWhiteOnMinibusBlue() {
        assertEquals(BadgeInk, badgeContentColor(TransitColors.Bus))
        assertEquals(BadgeInk, badgeContentColor(TransitColors.CableCar))
        assertEquals(BadgeInk, badgeContentColor(TransitColors.MetroLine1))
        assertEquals(BadgeInk, badgeContentColor(TransitColors.MetroLine2))
        assertEquals(Color.White, badgeContentColor(TransitColors.Minibus))
    }

    @Test
    fun contrastRatioMatchesWcagExtremes() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(TransitColors.Bus, TransitColors.Bus), 0.001f)
        assertEquals(contrastRatio(Color.Black, Color.White), contrastRatio(Color.White, Color.Black))
    }
}
