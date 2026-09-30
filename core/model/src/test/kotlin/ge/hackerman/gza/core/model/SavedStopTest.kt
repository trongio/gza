package ge.hackerman.gza.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SavedStopTest {
    private val stop = StopId("1:970")
    private val r326 = RouteId("1:R97493")
    private val r301 = RouteId("1:R29981")

    @Test
    fun `an empty filter shows every route`() {
        val saved = SavedStop(stop, walkMinutes = 4, routeFilter = emptySet())
        assertTrue(saved.shows(r326))
        assertTrue(saved.shows(r301))
    }

    @Test
    fun `a filter shows only its routes`() {
        val saved = SavedStop(stop, walkMinutes = 4, routeFilter = setOf(r326))
        assertTrue(saved.shows(r326))
        assertFalse(saved.shows(r301))
    }

    @Test
    fun `walk minutes must be within an hour`() {
        SavedStop(stop, 0, emptySet())
        SavedStop(stop, 60, emptySet())
        assertFailsWith<IllegalArgumentException> { SavedStop(stop, -1, emptySet()) }
        assertFailsWith<IllegalArgumentException> { SavedStop(stop, 61, emptySet()) }
    }

    @Test
    fun `buffer is zero to three minutes and defaults to one`() {
        assertEquals(1, UserSettings.DEFAULT_BUFFER_MINUTES)
        assertTrue(UserSettings.DEFAULT_BUFFER_MINUTES in UserSettings.BUFFER_MINUTES_RANGE)
        UserSettings(0, firstRunSuggestionHandled = false)
        UserSettings(3, firstRunSuggestionHandled = true)
        assertFailsWith<IllegalArgumentException> { UserSettings(-1, false) }
        assertFailsWith<IllegalArgumentException> { UserSettings(4, false) }
    }
}
