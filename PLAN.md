# Gza: product and engineering plan

Gza (Georgian გზა, "the way") is an unofficial Android app for Tbilisi public transport,
built on the same public gateway the official web app uses. The official app answers
"where is the bus". Gza answers the question people actually have:

> **When do I leave, and when will the bus really be here?**

Status: MVP in progress, executed task by task from `docs/tasks/BACKLOG.md`.
The single-stop prototype `ttc-leave-by` (copied to `docs/reference/ttc-leave-by/`)
(timetable plus GPS parked-bus detection for one stop) proved the approach on a real
phone on 2026-09-28 and becomes the seed of Phase 1.

---

## 1. Why the official app is confusing

Found while building the prototype, all verified against live data. See `docs/TTC_API.md`.

1. **The arrivals board lies at first and last stops.** At a terminus, "arrives in X min" counts
   buses *pulling in* to end their previous trip. Observed: route 326 showed "0 min"
   while its bus sat parked with a timetable departure 38 minutes away.
2. **Real-time delays contain garbage.** The trip planner reported `arrivalDelay: 3676`
   (61 min) and `3030` on legs that were running roughly on time.
3. **No "leave by" concept.** Everything is "bus in N min", and the user has to subtract
   their own walk in their head, per bus, per route.
4. **Stops and routes are split across screens.** Answering "which of my three buses
   do I catch" takes three pages.
5. **Mixed locales.** Some fields come back in Georgian even with `locale=en`
   (e.g. minibus `longName`).

Gza fixes these in the data layer, not with UI copy.

---

## 2. Feature set

### 2.0 Layover-aware ETAs (the headline fix)
The official app's biggest flaw: a bus resting at its first or last stop always shows
**"arrives in 0 min"**, even though it is waiting for its scheduled departure or a
driver break. Gza treats a parked bus as *waiting*, not *arriving*:

- **Detect layover:** a vehicle with `heading` and `nextStopId` both null within 500 m
  of the first stop of its pattern is in layover (fixtures: lay-by buses waited 318 m
  and 442 m from the stop, so ~120 m missed them; the null heading, not the distance,
  keeps moving buses out). The gateway sends no speed, so "no movement across two
  polls" for a bus with a stale heading is left to a later phase.
  A bus that just reached the **last** stop of pattern A is expected to start pattern B
  (the reverse direction) next.
- **Predict its real departure:** max(next timetable departure of that terminus, arrival
  time + minimum turnaround). Until Phase 6 learns it per route, the turnaround floor is
  2 min from the poll that first saw the bus parked. Never "now" unless the timetable
  says now.
- **Propagate downstream:** for any stop along the route, the ETA of a laid-over bus is
  its predicted terminus departure plus the travel time to that stop, not
  distance / speed from a standing start. So a stop 10 minutes down the line shows
  "~17:23 (bus waiting at the terminus until 17:13)" instead of "3 min".
- **Show it honestly:** chip `waiting at terminus, leaves 17:13`, and a countdown to
  *that* time. If the scheduled time passes and the bus hasn't moved, the chip switches
  to `late departure` and the ETA moves forward with each poll.
- **Test fixtures:** the recorded board where 326 read "0 min" with a 38-minute
  layover, and positions of buses parked at #970, are regression tests from day one.

### 2.1 Now screen (home)
- One big answer: **"Leave in 7 min"** for the best option to where you usually go
  from where you are right now.
- Below it, a merged departure list for your saved stops: route badge, destination,
  *predicted* departure, leave-by time, confidence chip.
- Chips mean something specific: `at stop` (GPS says a bus is parked at a terminus),
  `on its way, 4 stops away`, `running 6 min late`, `timetable only`.
- Swipe between saved places (Home, Work, custom). Auto-picks the place nearest to
  your GPS position.

### 2.2 Prediction engine (the core of "on steroids")
Every departure gets a **predicted time plus a confidence band**, computed on device from
four sources, in this order of trust:

| Source | Used for | Notes |
|---|---|---|
| Vehicle GPS positions | Where each bus physically is | Polled per route, 10 to 20 s while visible |
| Route polylines + stop order | Distance still to travel | Cached; project GPS onto polyline |
| Learned segment speeds | Seconds per segment by hour/weekday | Built locally from observed positions |
| TTC realtime `arrivalDelay` / arrivals board | Cross-check only | Discarded when it disagrees with GPS or exceeds sanity bounds |
| Timetable | Floor at termini, fallback elsewhere | A bus never leaves a first stop early |

Rules:
- **Terminus:** a bus parked (heading and next stop both null) within 500 m of the first
  stop means an on-time departure, no sooner than 2 min after it was first seen.
  No bus parked means prediction = max(timetable, ETA of the inbound bus to the
  terminus + turnaround time learned per route).
- **Mid-route stop:** ETA = remaining polyline distance / learned speed for the upcoming
  segments at this hour, blended with the timetable offset when GPS is stale.
- **Outlier filter:** reject TTC delays over 20 min unless GPS agrees; clamp negative
  values; drop GPS fixes with `heading: null` far from a terminus (bus is off duty).
- **Confidence:** from GPS age, number of segments ahead, and historical error for that
  route and hour. Shown as `±1 min`, `±4 min`, or `timetable only`.
- **Self-evaluation:** when a tracked bus actually passes a stop, log predicted vs.
  actual. That is the accuracy dashboard in Settings, and the training data for the
  segment speeds. All of it stays on device.

### 2.3 Leave-by calculations
- Walk time per saved place and stop: first estimate from the planner's WALK leg (real
  street distance), then corrected by the user's measured walking speed (GPS while the
  app is tracking a trip, opt-in).
- A **buffer** setting: 0 to 3 min, default 1.
- A leave-by time for every row, and the hero countdown down to the second in the last
  minute.
- The "Miss it?" fallback line always shows the next viable option, including other
  routes that serve the same destination.

### 2.4 Trip planner
- Backed by TTC `/v2/plan`: `leaveNow`, `departAt` or `arriveBy`, with `quick` or
  `lessWalking` optimization and modes bus / metro / gondola.
- Gza **re-ranks** the itineraries with its own predictions, not TTC's raw delays.
- Per-transfer **miss risk**: the chance of catching leg 2, given leg 1's predicted
  arrival and leg 2's confidence band. Shown as "tight transfer" or "safe transfer".
- "Arrive by 9:00" gives: leave home at 8:12, backup 8:21 (arrive 9:04).
- Address search through TTC geocode (Tbilisi bbox) plus offline stop-name search over
  the cached 2,753 stops, in Georgian, Latin transliteration and Russian.

### 2.5 Commute planner (pre-planning)
- Recurring trips: "weekdays, arrive at work by 9:30".
- The night before, and again 30 min before, WorkManager recomputes the leave time. A
  notification says "Leave at 8:47 for the 326" and updates itself if the prediction
  moves.
- A calendar-style view of the week's commutes with their predicted leave times.

### 2.6 Live trip mode
- Start from any itinerary. A foreground service tracks your GPS and the buses you rely on.
- Ongoing notification: "326 arrives in 3 min, 2 stops away". "Get off at the next stop."
  "Transfer: 472 leaves in 5 min from across the road."
- Automatic re-plan if a transfer becomes impossible.

### 2.7 Stop, route and map as one connected view
The official app splits stops, schedules and the live map into separate sections that
don't link to each other. In Gza they are one flow, and every departure row is a door
into the map.

**Stop view to map in one tap.**
- Tap any departure row: the map opens on **that exact bus**, with the route line, your
  stop pinned, and the bus's path to it highlighted.
- A bottom sheet (Material 3 `BottomSheetScaffold`) keeps the departure list visible
  over the map. Drag it up for the list, down for the map. Nothing is lost going back
  and forth.
- A shared-element transition carries the route badge from the row onto the bus marker,
  so it's obvious which bus you are looking at.

**"Buses coming to my stop" view (route-to-stop).**
- Pick a stop and a route: the map shows **every bus on that route heading toward your
  stop**, each labelled with its predicted arrival ("4 min", "11 min", "waiting at
  terminus, leaves 17:13").
- Only the stretch of route *before* your stop is drawn strongly; the part after it is
  dimmed, because buses there have already passed.
- Buses resting at the terminus get a distinct parked marker with the time they leave.
- A strip along the bottom lays the route out as a straight line of stops (like a metro
  diagram) with the buses as dots moving along it. It's easier to read than the map
  when you only want to know "how many stops away".
- "All routes" mode: every bus from every route that serves this stop, coloured by
  route, to answer "which bus gets here first".

**Following a bus.**
- Tap a bus marker: the camera follows it, and its sheet shows the stops ahead with a
  predicted time at each one, your stop highlighted.
- Markers move smoothly between GPS fixes (interpolated along the polyline, not
  straight lines across buildings), with heading arrows.
- "Notify me when it's 2 stops away" from the bus sheet.

**Map to stop, too.**
- Tap any stop pin: its merged departures slide up, same component as the Now screen.
- Long-press anywhere on the map: "Plan a trip from here / to here".
- Your location dot, with walking time to each nearby stop shown on its pin.

**Map tech.**
- MapLibre with OpenFreeMap vector tiles (free, no key; not TTC's Mapbox token), with a light and
  dark style matching the app theme.
- Route polylines and stop order come from the cache, so the map draws instantly and
  only the bus positions need the network.
- Positions are polled only for the routes on screen, every 10 s while the map is visible.

### 2.8 Glanceable surfaces
- Home screen widget (Glance): the next leave-by for a chosen place, refreshed by
  WorkManager plus a local alarm tick.
- Quick Settings tile: "Next bus home".
- App shortcuts: "Go home", "Go to work".

### 2.9 Nearby
- Fused location lists the stops within 500 m with their merged departures.
- "I'm at a stop now" mode: detects the nearest stop and shows everything leaving it.

### 2.10 Settings and data
- Language: ka / en / ru (the API supports all three).
- Walking speed, buffer, default modes, optimization preference.
- Local history browser and export (JSON) of logged predictions for the curious.
- Everything stored on device. No accounts, no analytics.

---

## 3. Architecture

Kotlin and Jetpack Compose. The toolchain matches `gree-local` (Gradle 8.11.2, Kotlin
2.2.20, compileSdk 36, minSdk 26) so the build environment is already on this machine.

```
:core:ttc        Pure JVM. TTC gateway client, DTOs, config fetch. kotlinx.serialization + OkHttp.
:core:predict    Pure JVM. Prediction engine, polyline projection, outlier filters, leave-by math.
                 No android.* imports, so it is 100% unit testable with recorded fixtures.
:core:data       Android. Room database, DataStore, repositories, caching policy.
:feature:now     Now screen and saved places.
:feature:plan    Trip planner, itinerary details, commute planner.
:feature:trip    Live trip mode and its foreground service.
:feature:map     MapLibre map.
:widget          Glance widget and Quick Settings tile.
:app             Navigation, Hilt DI wiring, theme.
```

### 3.1 Storage (Room)
| Table | Contents | Refresh |
|---|---|---|
| `stops` | 2,753 stops: id, code, names (en and ka side by side), lat/lon, mode | Weekly (WorkManager) |
| `routes` | 280 routes: short and long names (en/ka), color, kind | Weekly (WorkManager) |
| `stop_routes` | Which routes serve a stop, for saved stops offline | On first use, then weekly |
| `patterns` | A route's patterns with headsigns and termini (en/ka); the default pattern is never stored | On first use of the route, then on app open if older than 12 h and used in the last 14 days |
| `pattern_stops` | Stop order per pattern | Same as `patterns` |
| `polylines` | Encoded polyline per pattern | Same as `patterns` |
| `schedule_periods`, `schedule_stop_times` | Service periods with their dates; per stop row, the service minutes packed as 16-bit values (about 50 KB per busy route) | Same as `patterns` |
| `sync_state` | When each of the above was last synced and used | With each sync |
| `places` | Saved places with walk times (saved stops, walk times and route filters are in DataStore since T04) | User |
| `commutes` | Recurring trips | User |
| `observations` | GPS fixes of tracked buses (rolling 30 days) | Live |
| `segment_speeds` | Learned seconds per segment by hour bucket and day type | Nightly WorkManager |
| `prediction_log` | Predicted vs. actual pass times | Live |

### 3.2 Networking and politeness
- Only poll what is on screen. Positions for routes the user is looking at, never all 280.
- Back off exponentially on errors; stop polling in the background except during live
  trip mode.
- Stale-while-revalidate for everything static; the app opens instantly from Room.

### 3.3 Gateway key handling
The web app gets `PIS_GATEWAY_KEY` from TTC's Firebase Remote Config at runtime, so the
key can rotate.
- **Runtime:** Gza does the same thing through the Firebase Installations and Remote
  Config REST endpoints, caches the key in DataStore, and re-fetches it on any 401 or 403.
- **Build time:** `tools/fetch-ttc-config.sh` pulls the current web config and writes
  `ttc.properties` (gitignored), which is read into `BuildConfig` as a fallback. No key
  is ever committed to this repository.

### 3.4 Permissions
- `ACCESS_FINE_LOCATION`, requested when the user first taps Nearby or "use my location".
- `POST_NOTIFICATIONS` for commute alerts.
- `FOREGROUND_SERVICE_LOCATION`, only during live trip mode.
- No background location outside an active trip.

---

## 4. Phases

| Phase | Scope | Done when |
|---|---|---|
| 0. Foundations | Module layout, CI (GitHub Actions: unit tests + assembleDebug), `:core:ttc` client with recorded JSON fixtures, runtime key fetch | `./gradlew test` is green on CI; the key refreshes after a forced 401 |
| 1. Now screen | Port the ttc-leave-by logic; layover-aware ETAs (2.0); saved places and stops; merged departures; terminus parked detection; leave-by + buffer; Room cache | Replaces ttc-leave-by on the phone |
| 2. Prediction v1 | Polyline projection, GPS ETA for mid-route stops, outlier filter, confidence chips, prediction log | Logged median error below 2 min on the user's own routes over a week |
| 3. Planner | `/plan` integration, re-ranking, transfer risk, arrive-by, address + offline stop search | "Arrive by" gives a leave-home time and a backup |
| 4. Commutes + widget | Recurring trips, WorkManager notifications, Glance widget, QS tile, shortcuts | Morning notification fires with a correct time 5 weekdays in a row |
| 5. Live trip + map | Foreground trip service, re-planning; connected stop-to-map flow (2.7): tap a row to open that bus, buses-coming-to-my-stop view, line diagram, follow a bus | End-to-end trip with one transfer, guided by notifications |
| 6. Learning | Segment speeds from observations, per-route turnaround model, accuracy dashboard | Prediction error measurably drops versus v1 |
| 7. Polish + release | ka/en/ru strings, accessibility pass, Play listing marked unofficial, privacy policy | On Play as an internal test, then production |

---

## 5. Testing
- `:core:predict` and `:core:ttc` are pure JVM. Real API responses recorded with
  `tools/record-fixtures.sh` become JUnit fixtures, including the known-bad cases (the
  terminus board, the 3,676 s delay, times past midnight, week rollover of
  `serviceDates`).
- Replay tests: a recorded 30-minute trace of positions for a route is played back
  against the engine, and its predictions are scored against the actual pass times in
  the trace.
- Compose UI tests for the Now screen states: loading, offline, no more buses, tomorrow.
- On-device check on the S22 at every phase gate.

---

## 6. Risks
| Risk | Mitigation |
|---|---|
| Unofficial API changes or blocks third parties | Thin client layer with fixtures, so breakage shows up in tests fast; cache aggressively so the app degrades to the timetable, not to nothing |
| Key rotation | Runtime Remote Config fetch, re-fetch on 401 |
| Load on TTC's servers | Poll only visible routes; shared request cache; no background polling except live trips |
| Trademark | Never use the TTC name or logo in the app title or icon; listing says unofficial |
| Bad real-time data | GPS is the source of truth; TTC delays are a cross-check only |
| Battery | Polling tied to screen visibility; foreground service only in trip mode |
