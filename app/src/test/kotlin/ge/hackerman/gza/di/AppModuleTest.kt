package ge.hackerman.gza.di

import ge.hackerman.gza.core.model.TBILISI_ZONE
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

// Never assert on the key itself: CI builds with a dummy, local builds with the real one.
class AppModuleTest {
    @Test
    fun `clock runs in Tbilisi time`() {
        assertEquals(TBILISI_ZONE, AppModule.provideClock().zone)
    }

    @Test
    fun `fallback config has an https gateway url`() {
        val url = AppModule.provideTtcFallbackConfig().gatewayBaseUrl
        assertTrue(url.isNotBlank())
        assertTrue(url.startsWith("https://"))
    }
}
