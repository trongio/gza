package ge.hackerman.gza.core.model

import java.time.ZoneId

/** Every local time in the app is Tbilisi time, never the device zone. */
val TBILISI_ZONE: ZoneId = ZoneId.of("Asia/Tbilisi")

/** The box the TTC web app passes to geocoding; verified to return Tbilisi results. */
val TBILISI_BOUNDS: BoundingBox = BoundingBox(minLon = 44.6, minLat = 41.6, maxLon = 45.0, maxLat = 41.85)
