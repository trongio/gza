package ge.hackerman.gza.core.model

import java.time.ZoneId

/** Every local time in the app is Tbilisi time, never the device zone. */
val TBILISI_ZONE: ZoneId = ZoneId.of("Asia/Tbilisi")
