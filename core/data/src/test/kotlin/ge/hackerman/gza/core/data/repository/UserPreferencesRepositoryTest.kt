package ge.hackerman.gza.core.data.repository

import app.cash.turbine.test
import ge.hackerman.gza.core.data.datastore.DataStoreFiles
import ge.hackerman.gza.core.data.testing.TestDataStores
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.SavedStop
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.UserSettings
import java.io.File
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class UserPreferencesRepositoryTest {
    @TempDir
    lateinit var dir: File

    private val stores by lazy { TestDataStores(dir) }
    private val s970 = StopId("1:970")
    private val s972 = StopId("1:972")
    private val s824 = StopId("1:824")
    private val r326 = RouteId("1:R97493")
    private val r301 = RouteId("1:R29981")

    @AfterEach
    fun tearDown() = stores.closeAll()

    private fun repository(): UserPreferencesRepository = DataStoreUserPreferencesRepository(stores.userPreferences())

    private suspend fun UserPreferencesRepository.ids() = savedStops.first().map { it.stopId }

    private fun writeFile(json: String) {
        stores.file(DataStoreFiles.USER_PREFERENCES).apply { parentFile.mkdirs() }.writeText(json)
    }

    @Test
    fun `defaults are no stops and a one minute buffer`() = runBlocking {
        val repo = repository()
        assertEquals(emptyList(), repo.savedStops.first())
        assertEquals(UserSettings(bufferMinutes = 1, firstRunSuggestionHandled = false), repo.settings.first())
    }

    @Test
    fun `save, reorder and remove keep display order`() = runBlocking {
        val repo = repository()
        repo.saveStop(s970, 4)
        repo.saveStop(s972, 6)
        repo.saveStop(s824, 10)
        assertEquals(listOf(s970, s972, s824), repo.ids())
        repo.moveStop(s824, 0)
        assertEquals(listOf(s824, s970, s972), repo.ids())
        repo.moveStop(s824, 99)
        assertEquals(listOf(s970, s972, s824), repo.ids())
        repo.moveStop(s970, -3)
        assertEquals(listOf(s970, s972, s824), repo.ids())
        repo.removeStop(s972)
        assertEquals(listOf(s970, s824), repo.ids())
    }

    @Test
    fun `saving twice keeps one entry with its walk and filter`() = runBlocking {
        val repo = repository()
        repo.saveStop(s970, 4)
        repo.setRouteFilter(s970, setOf(r326))
        repo.saveStop(s970, 9)
        assertEquals(listOf(SavedStop(s970, 4, setOf(r326))), repo.savedStops.first())
    }

    @Test
    fun `walk minutes are clamped on write`() = runBlocking {
        val repo = repository()
        repo.saveStop(s970, -5)
        assertEquals(0, repo.savedStops.first().single().walkMinutes)
        repo.setWalkMinutes(s970, 500)
        assertEquals(60, repo.savedStops.first().single().walkMinutes)
        repo.setWalkMinutes(s970, 7)
        assertEquals(7, repo.savedStops.first().single().walkMinutes)
    }

    @Test
    fun `a hand written file is clamped and cleaned on read`() = runBlocking {
        writeFile(
            """
            {"savedStops":[
              {"stopId":"1:970","walkMinutes":-5,"routeFilter":["1:R97493","R301"]},
              {"stopId":"970","walkMinutes":3},
              {"stopId":"1:970","walkMinutes":8},
              {"stopId":"1:824","walkMinutes":99}
            ],"bufferMinutes":12,"future":"field"}
            """.trimIndent()
        )
        val repo = repository()
        assertEquals(
            listOf(SavedStop(s970, 0, setOf(r326)), SavedStop(s824, 60, emptySet())),
            repo.savedStops.first()
        )
        assertEquals(3, repo.settings.first().bufferMinutes)
        // Indices follow what is shown, not the raw file.
        repo.moveStop(s824, 0)
        assertEquals(listOf(s824, s970), repo.ids())
    }

    @Test
    fun `buffer is clamped to zero to three`() = runBlocking {
        val repo = repository()
        repo.setBufferMinutes(-1)
        assertEquals(0, repo.settings.first().bufferMinutes)
        repo.setBufferMinutes(10)
        assertEquals(3, repo.settings.first().bufferMinutes)
        repo.setBufferMinutes(2)
        assertEquals(2, repo.settings.first().bufferMinutes)
    }

    @Test
    fun `route filter round trips and empty means every route`() = runBlocking {
        val repo = repository()
        repo.saveStop(s970, 4)
        repo.setRouteFilter(s970, setOf(r326, r301))
        assertEquals(setOf(r326, r301), repo.savedStops.first().single().routeFilter)
        repo.setRouteFilter(s970, emptySet())
        val saved = repo.savedStops.first().single()
        assertEquals(emptySet(), saved.routeFilter)
        assertEquals(true, saved.shows(r301))
    }

    @Test
    fun `changes to a stop that is not saved do nothing`() = runBlocking {
        val repo = repository()
        repo.saveStop(s970, 4)
        repo.setWalkMinutes(s824, 9)
        repo.setRouteFilter(s824, setOf(r326))
        repo.moveStop(s824, 0)
        repo.removeStop(s824)
        assertEquals(listOf(SavedStop(s970, 4, emptySet())), repo.savedStops.first())
    }

    @Test
    fun `everything persists across a new store on the same file`() = runBlocking {
        repository().run {
            saveStop(s970, 4)
            setRouteFilter(s970, setOf(r326))
            setBufferMinutes(2)
            markFirstRunSuggestionHandled()
        }
        stores.closeAll()
        val reopened = repository()
        assertEquals(listOf(SavedStop(s970, 4, setOf(r326))), reopened.savedStops.first())
        assertEquals(UserSettings(2, firstRunSuggestionHandled = true), reopened.settings.first())
    }

    @Test
    fun `a corrupt file falls back to the defaults`() = runBlocking {
        writeFile("{ broken")
        val repo = repository()
        assertEquals(emptyList(), repo.savedStops.first())
        repo.saveStop(s970, 4)
        assertEquals(listOf(s970), repo.ids())
    }

    @Test
    fun `saved stops emit on change and not on unrelated writes`() = runBlocking {
        val repo = repository()
        repo.savedStops.test {
            assertEquals(emptyList(), awaitItem())
            repo.saveStop(s970, 4)
            assertEquals(listOf(s970), awaitItem().map { it.stopId })
            repo.setBufferMinutes(3)
            repo.setWalkMinutes(s970, 5)
            assertEquals(5, awaitItem().single().walkMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
