package ge.hackerman.gza.core.predict.testing

import ge.hackerman.gza.core.model.TBILISI_ZONE
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * A fixed clock at [local] Tbilisi time (`2026-09-28T17:32:00`). Its zone is UTC on purpose:
 * the engine must read Tbilisi time whatever zone the injected clock carries.
 */
fun tbilisiClock(local: String): Clock = tbilisi(local).toInstant().clock()

/** [local] as a Tbilisi wall-clock time. */
fun tbilisi(local: String): ZonedDateTime = LocalDateTime.parse(local).atZone(TBILISI_ZONE)

fun Instant.clock(): Clock = Clock.fixed(this, ZoneOffset.UTC)
