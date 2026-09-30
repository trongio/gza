package ge.hackerman.gza.core.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.database.PackedMinutes
import ge.hackerman.gza.core.data.model.Freshness
import ge.hackerman.gza.core.data.testing.FakeTtcGatewayClient
import ge.hackerman.gza.core.data.testing.FixtureDomain
import ge.hackerman.gza.core.data.testing.MutableClock
import ge.hackerman.gza.core.data.testing.TestDatabase
import ge.hackerman.gza.core.model.ServiceMinute
import ge.hackerman.gza.core.model.TBILISI_ZONE
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Nothing stored or computed depends on the device's zone: Tbilisi is only ever explicit. */
@RunWith(AndroidJUnit4::class)
class TimeZoneIndependenceTest {
    private val original: TimeZone = TimeZone.getDefault()

    @After
    fun restoreZone() = TimeZone.setDefault(original)

    private data class Snapshot(
        val minutes: List<List<Int>>,
        val dates: List<List<LocalDate>>,
        val freshness: List<Freshness>
    )

    private fun snapshotIn(zone: String): Snapshot = runBlocking {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        val db = TestDatabase.inMemory()
        try {
            val clock = MutableClock()
            val policy = StalenessPolicy()
            RouteSync(FakeTtcGatewayClient(), db, SyncStatusTracker(clock), policy, clock)
                .syncIfStale(FixtureDomain.routeId("301"))
            val rows = db.scheduleDao().observeAtStop(FixtureDomain.STOP_970).first()
            val syncedAt = db.syncStateDao().get("route:" + FixtureDomain.routeId("301").value)!!.syncedAt
            Snapshot(
                minutes = rows.map { row -> PackedMinutes.unpack(row.times!!).map { it.minutes } },
                dates = rows.map { it.serviceDates },
                freshness = listOf(Duration.ofHours(11), Duration.ofHours(12), Duration.ofMinutes(-4)).map {
                    policy.freshness(syncedAt, policy.routeMaxAge, clock.now + it)
                }
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun `sync and read give the same answers in any device zone`() {
        val tbilisi = snapshotIn("Asia/Tbilisi")
        assertEquals(listOf(Freshness.FRESH, Freshness.STALE, Freshness.FRESH), tbilisi.freshness)
        assertEquals(true, tbilisi.minutes.flatten().any { it >= 1440 })
        assertEquals(tbilisi, snapshotIn("America/New_York"))
        assertEquals(tbilisi, snapshotIn("Pacific/Kiritimati"))
    }

    @Test
    fun `a service minute past midnight is a Tbilisi time on the next calendar day`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
        val at = ServiceMinute(1445).atDate(LocalDate.of(2026, 9, 28))
        assertEquals(TBILISI_ZONE, at.zone)
        assertEquals(LocalDateTime.of(2026, 9, 29, 0, 5), at.toLocalDateTime())
    }
}
