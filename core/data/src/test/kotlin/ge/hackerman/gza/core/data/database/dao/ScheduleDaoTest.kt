package ge.hackerman.gza.core.data.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import ge.hackerman.gza.core.data.database.PackedMinutes
import ge.hackerman.gza.core.data.database.entity.SchedulePeriodEntity
import ge.hackerman.gza.core.data.database.entity.ScheduleStopTimesEntity
import ge.hackerman.gza.core.data.database.routeDataRows
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.ServiceMinute
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ScheduleDaoTest : DaoTest() {
    private val dao get() = db.scheduleDao()

    private suspend fun store(name: String) {
        val id = FixtureDomain.routeId(name)
        val suffixes = FixtureDomain.suffixes(id)
        val rows = routeDataRows(
            FixtureDomain.route(id, Language.EN),
            null,
            suffixes.map { FixtureDomain.patternStops(id, it) },
            suffixes.map { FixtureDomain.schedule(id, it) },
            FixtureDomain.polylines(id)
        )
        db.routeDataDao().replaceRouteData(id.value, rows, synced("route:${id.value}"))
    }

    @Test
    fun `rows at 1-970 come from every route and pattern that serves it`() = runTest {
        listOf("301", "326", "551", "472").forEach { store(it) }
        dao.observeAtStop(FixtureDomain.STOP_970).test {
            val rows = awaitItem()
            assertTrue(rows.all { it.stopId == FixtureDomain.STOP_970 })
            val byPattern = rows.groupBy { it.routeId to it.suffix }.mapValues { it.value.size }
            val r301 = FixtureDomain.routeId("301").value
            val r326 = FixtureDomain.routeId("326").value
            val r551 = FixtureDomain.routeId("551").value
            assertEquals(
                mapOf(
                    (r301 to "0:01") to 3,
                    (r301 to "1:01") to 3,
                    (r326 to "0:01") to 2,
                    (r326 to "1:01") to 2,
                    (r551 to "0:01") to 3,
                    (r551 to "1:01") to 3
                ),
                byPattern
            )
            val weekday301 = rows.first { it.routeId == r301 && it.suffix == "0:01" && it.periodIndex == 0 }
            assertEquals(DayOfWeek.MONDAY, weekday301.fromDay)
            assertEquals(ServiceMinute(427), PackedMinutes.unpack(weekday301.times!!).first())
        }
    }

    @Test
    fun `a time past midnight comes back above 1440`() = runTest {
        store("326")
        dao.observeAtStop("1:3569").test {
            val minutes = awaitItem().flatMap { PackedMinutes.unpack(it.times!!) }.map { it.minutes }
            assertTrue(1445 in minutes)
        }
    }

    @Test
    fun `service dates round trip, a past week and an empty list unchanged`() = runTest {
        val lastWeek = listOf(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22))
        val periods = listOf(
            SchedulePeriodEntity("1:R", "0:01", 0, DayOfWeek.MONDAY, DayOfWeek.FRIDAY, lastWeek),
            SchedulePeriodEntity("1:R", "0:01", 1, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, emptyList())
        )
        val times = PackedMinutes.pack(listOf(ServiceMinute(600)))
        val stopTimes = listOf(
            ScheduleStopTimesEntity("1:R", "0:01", 0, 0, 1, "1:970", times),
            ScheduleStopTimesEntity("1:R", "0:01", 1, 0, 1, "1:970", times)
        )
        db.routeDataDao().replaceRouteData(
            "1:R",
            RouteDataRows(emptyList(), emptyList(), emptyList(), periods, stopTimes, emptyList(), null),
            synced("route:1:R")
        )
        dao.observeSchedule("1:R", "0:01").test {
            val rows = awaitItem()
            assertEquals(lastWeek, rows[0].serviceDates)
            assertEquals(emptyList<LocalDate>(), rows[1].serviceDates)
            assertEquals(DayOfWeek.SATURDAY, rows[1].fromDay)
        }
    }

    @Test
    fun `a period that skips the stop is still listed, with no row`() = runTest {
        val periods = listOf(
            SchedulePeriodEntity("1:R", "0:01", 0, DayOfWeek.MONDAY, DayOfWeek.FRIDAY, emptyList()),
            SchedulePeriodEntity("1:R", "0:01", 1, DayOfWeek.SUNDAY, DayOfWeek.SUNDAY, emptyList())
        )
        val times = PackedMinutes.pack(listOf(ServiceMinute(600)))
        val stopTimes = listOf(
            ScheduleStopTimesEntity("1:R", "0:01", 0, 0, 1, "1:970", times),
            ScheduleStopTimesEntity("1:R", "0:01", 1, 0, 1, "1:1", times)
        )
        db.routeDataDao().replaceRouteData(
            "1:R",
            RouteDataRows(emptyList(), emptyList(), emptyList(), periods, stopTimes, emptyList(), null),
            synced("route:1:R")
        )
        dao.observeAtStop("1:970").test {
            val rows = awaitItem()
            assertEquals(listOf(0, 1), rows.map { it.periodIndex })
            assertNull(rows[1].stopId)
            assertNull(rows[1].times)
        }
        dao.observeAtStop("1:404").test { assertEquals(emptyList<ScheduleRow>(), awaitItem()) }
    }
}
