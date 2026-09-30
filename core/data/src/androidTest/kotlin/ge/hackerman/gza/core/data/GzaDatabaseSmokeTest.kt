package ge.hackerman.gza.core.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.database.GzaDatabase
import ge.hackerman.gza.core.data.database.PackedMinutes
import ge.hackerman.gza.core.data.database.dao.RouteDataRows
import ge.hackerman.gza.core.data.database.entity.PatternEntity
import ge.hackerman.gza.core.data.database.entity.PatternStopEntity
import ge.hackerman.gza.core.data.database.entity.SchedulePeriodEntity
import ge.hackerman.gza.core.data.database.entity.ScheduleStopTimesEntity
import ge.hackerman.gza.core.data.database.entity.SyncStateEntity
import ge.hackerman.gza.core.model.ServiceMinute
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real framework SQLite on a device: a route written, read through a flow, and still there after reopening. */
@RunWith(AndroidJUnit4::class)
class GzaDatabaseSmokeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun open(): GzaDatabase = Room.databaseBuilder<GzaDatabase>(context, NAME)
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @Before
    @After
    fun deleteFile() {
        context.deleteDatabase(NAME)
    }

    @Test
    fun aRouteSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        val rows = RouteDataRows(
            patterns = listOf("0:01", "1:01").map {
                PatternEntity("1:R", it, it.first().digitToInt(), null, null, null, null, null, null, "Head $it", null)
            },
            patternStops = listOf(
                PatternStopEntity("1:R", "0:01", 0, "1:970"),
                PatternStopEntity("1:R", "1:01", 0, "1:824")
            ),
            polylines = emptyList(),
            periods = listOf(
                SchedulePeriodEntity(
                    "1:R",
                    "0:01",
                    0,
                    DayOfWeek.MONDAY,
                    DayOfWeek.FRIDAY,
                    listOf(LocalDate.of(2026, 9, 28))
                )
            ),
            stopTimes = listOf(
                ScheduleStopTimesEntity(
                    "1:R",
                    "0:01",
                    0,
                    0,
                    1,
                    "1:970",
                    PackedMinutes.pack(listOf(ServiceMinute(427), ServiceMinute(1445)))
                )
            ),
            stopsFromPatterns = emptyList(),
            routeFromDetail = null
        )
        val first = open()
        first.routeDataDao().replaceRouteData("1:R", rows, SyncStateEntity("route:1:R", Instant.EPOCH))
        assertEquals(2, first.routeDataDao().loadRouteData("1:R", "route:1:R").patterns.size)
        first.close()

        val reopened = open()
        try {
            val atStop = reopened.scheduleDao().observeAtStop("1:970").first()
            assertEquals(listOf(427, 1445), PackedMinutes.unpack(atStop.single().times!!).map { it.minutes })
            assertEquals(listOf("0:01", "1:01"), reopened.routeDataDao().getPatterns("1:R").map { it.suffix })
        } finally {
            reopened.close()
        }
    }

    private companion object {
        const val NAME = "smoke-test.db"
    }
}
