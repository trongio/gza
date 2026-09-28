package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.TBILISI_ZONE
import java.time.Clock
import java.time.ZonedDateTime

/** Current time in Tbilisi, whatever zone the injected [Clock] was built with. */
fun Clock.nowInTbilisi(): ZonedDateTime = ZonedDateTime.now(this).withZoneSameInstant(TBILISI_ZONE)
