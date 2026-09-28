package ge.hackerman.gza.core.ttc.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock tests move by hand. */
class MutableClock(@Volatile var now: Instant = START, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun instant(): Instant = now

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    fun advanceBy(duration: Duration) {
        now += duration
    }

    companion object {
        val START: Instant = Instant.parse("2026-09-28T13:11:00Z")
    }
}
