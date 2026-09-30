# MVP backlog

Status: `[ ]` todo, `[~]` in progress (on its task branch), `[x]` done (PR number).
`/task` takes the first `[ ]` whose dependencies are all `[x]`.

**MVP goal:** replace the official app for daily commuting. Open the app and see when to
leave for your usual buses, with honest predictions (a bus waiting at a terminus is never
"arriving in 0 min"), find any stop, jump from a departure straight to that bus on the
map, see every bus coming to your stop, and plan a trip with an arrive-by time.

The first real user is at stop `1:970` (Ana Politkovskaia St), routes 301, 326, 551, all
of which start there. Use it for manual checks and fixtures.

---

## [x] T01 Project foundation (PR #1)
Depends: none
- Multi-module Gradle project per CLAUDE.md, `build-logic/` convention plugins, version
  catalog with the latest stable AGP, Kotlin, Compose BOM, Hilt, Room, KSP, Retrofit,
  OkHttp, kotlinx.serialization, Navigation, WorkManager, DataStore, MapLibre, JUnit 5,
  Turbine, Roborazzi. Application id `ge.hackerman.gza`, minSdk 26, target/compile the
  newest stable SDK installed.
- Spotless (ktlint), detekt, Android lint configured; `./gradlew spotlessCheck detekt lint test assembleDebug` green.
- GitHub Actions CI running that gate on every PR and on `main`. CI creates a dummy
  `ttc.properties` so the build never needs the real key.
- Empty app with Hilt, a theme (light/dark), and a placeholder screen that runs on the emulator.
- `maestro/` with one smoke flow (app launches, placeholder visible).

Acceptance:
- [x] Gate green locally and on CI for the T01 PR.
- [x] App installs and launches on the emulator; Maestro smoke flow passes.
- [x] No secret in the repo (`git grep` for the key and Firebase values finds nothing).

Follow-ups:
- Decide whether to require the `gate` CI check on `main` via branch protection.
- Robolectric runs at `sdk=36`: SDK 37 rendered the second screenshot blank; retry 37 on the next Robolectric release.
- Build-logic convention plugins have no Gradle TestKit tests (plugin order and the missing-application-plugin error were only checked by hand).

## [x] T02 Gateway config and key rotation (PR #2)
Depends: T01
- `:core:ttc` pure JVM. Fetch `PIS_GATEWAY_KEY` and base URL at runtime from TTC's
  Firebase Remote Config (Installations + fetch REST, as `tools/fetch-ttc-config.sh`
  does), cache it (interface so `:core:data` can back it with DataStore), fall back to
  `BuildConfig` values from `ttc.properties`.
- OkHttp interceptor adds `x-api-key`; on 401/403 it refetches the key once and retries.

Acceptance:
- [x] Unit tests with MockWebServer: first launch fetch, cached reuse, 401 then refetch then success, Remote Config down so fallback used.
- [x] Key never logged (test asserts the logging interceptor redacts the header).

Follow-ups:
- For T04: `FirebaseRemoteConfigClient` does not wrap `TtcConfigCache.readInstallation()` / `writeInstallation()`, so a throwing DataStore installation store makes every fetch fall back to the build-time key. Treat a read failure as "no installation" and writes as best effort, with tests (an installation read or write that throws still fetches the key).
- `DefaultGatewayConfigProvider.current()` cold branch does not record a backoff failure for a non-IO exception outside the wrapped calls (e.g. from `rules.fromCache`); unlikely, optional fix.
- Decide whether Settings shows where the gateway config came from (remote, cache, fallback).
- Firebase web credentials are baked in at build time; consider reading them from TTC's web page at runtime.

## [x] T03 Gateway client and fixtures (PR #4)
Depends: T02
- Retrofit service + DTOs for every endpoint in `docs/TTC_API.md`: stops, stop routes,
  arrival-times, routes, route detail, schedule, stops-of-patterns, polylines,
  positions, plan, geocode, reverse geocode. Map DTOs to `:core:model` domain types.
- `tools/record-fixtures.sh` records real responses (key stripped) into test resources,
  including stop 1:970 and routes 301, 326, 551, 472.

Acceptance:
- [x] Every endpoint parsed from a recorded fixture in a unit test, including the known-bad cases (board `0` at terminus, `arrivalDelay` 3676, negative `scheduledArrivalMinutes`, null heading).
- [x] Lenient parsing: unknown fields and nulls never crash.

Follow-ups:
- For T04: a sync that returns an empty stops or routes list must never replace a non-empty Room table (defence in depth on top of the client's all-misfit Malformed rule).
- Positions whose pattern keys are all invalid (gateway key format change) come back as 0 vehicles, not Malformed; polylines already apply the rule to keys (`PositionMappers.kt`).
- Positions: one fully bad pattern fails the whole response; consider returning the good patterns and marking only the bad one unknown.
- Plan: a leg whose `steps` or `intermediateStops` all misfit drops the whole itinerary; these are secondary detail and could become null instead.
- Route 469 detail returned HTTP 500 live on 2026-09-28 in en and ka (gateway side); record it as an error fixture if it persists.
- PLAN.md says the parked 326 waited "38 minutes"; the weekday timetable runs every 18 minutes. Confirm with the user and correct.

## [ ] T04 Local data layer and sync
Depends: T03
- `:core:data`: Room entities/DAOs for stops, routes, patterns, pattern stops, polylines,
  schedules; DataStore for preferences; repositories exposing Flows (Room is the source
  of truth, network refreshes it).
- Sync: stops and routes on first launch and weekly (WorkManager); a route's patterns,
  polylines and schedules on first use, then refreshed on app open if older than 12 h.

Acceptance:
- [ ] Room DAO tests (in-memory) and repository tests with a fake gateway.
- [ ] App works offline after one online launch (test: repository serves cached data when the gateway throws).

## [~] T05 Prediction engine v1: timetable + layover
Depends: T03
- `:core:predict` pure JVM with an injected `Clock`. Service period selection
  (serviceDates first, weekday fallback), times past midnight, departures for a stop
  across today and tomorrow.
- **Layover detection** (PLAN.md 2.0): buses parked at a terminus of their pattern are
  `Waiting(leavesAt)`, where leavesAt = max(next timetable departure, now + minimum
  turnaround). Never "arriving now".
- Leave-by = predicted departure minus walk minutes minus buffer.

Acceptance:
- [ ] Tests from real fixtures at stop 1:970: parked 326 with the board saying 0 min is predicted as waiting until the timetable time.
- [ ] Tests for Sunday to Monday rollover, 23:59 to 00:10 departures, empty service day, missing schedule.
- [ ] Ports and passes the test cases from the ttc-leave-by prototype.

Follow-ups:
- Warm-route late claim: when the route was polled moments ago, a parked bus with no memory past its time could claim the passed row as Late (rejected for cold start, see PLAN.md 2.0).
- Bus stale only via missedLongAgo but seen on every poll resets firstSeen; keep firstSeen and clear waitingFor instead.

## [x] T06 App shell and design system (PR #3)
Depends: T01
- `:core:designsystem`: color tokens (light/dark), typography (a display face, a body
  face, a mono face for times), route badge, departure row, chips (waiting, live,
  late, timetable only), countdown, bottom sheet scaffolding.
- Navigation: Now, Search, Map, Plan, Settings, with type-safe routes and bottom bar.
- Strings in en and ka.

Acceptance:
- [x] Roborazzi screenshots of each component in light and dark.
- [x] Every interactive element has a content description and a 48dp target.

Follow-ups:
- Georgian Settings tab label "პარამეტრები" truncates to "პარამეტ..." at default font size (and Plan/Settings at 1.5x); needs a shorter ka label chosen by the user, or a different label layout.
- Georgian wording check by the user: "გამოდით" vs "გადით" for "Leave in N min".
- Search and Plan placeholder state lives in the navigation `composable {}` blocks; T07/T08/T14 must move it into `XRoute` + ViewModel.
- `docs/tasks/plans/T06.md` sections 3 and 5 still describe the old `sheet_drag_handle` string and Material's drag handle slot; the scaffold now takes `sheetLabel` and exposes Expanded/Collapsed state.

## [ ] T07 Now screen
Depends: T04, T05, T06
- Saved stops (favourites) with a walk time each, and route filters per stop.
- Merged departure list across saved stops: route badge, destination, predicted
  departure, leave-by, live chip; hero card "Leave in N min" with the fallback option.
- Live refresh: positions polled every 15 s for the routes shown, only while visible.
- First-run: add stop 1:970 as a suggestion, walk time editable.

Acceptance:
- [ ] ViewModel tests with Turbine: loading, data, offline cached, no more buses today, tomorrow section.
- [ ] Screenshot tests of those states; Maestro flow: add a stop, see departures, change walk time.
- [ ] On the emulator with live data, stop 1:970 shows parked buses as waiting, never "0 min".

## [ ] T08 Stop search and stop detail
Depends: T04, T06
- Offline search over cached stops by name (en, ka) and stop code, tolerant of Latin
  transliteration of Georgian ("politkovskaia", "vazha").
- Stop detail: all routes serving the stop, merged departures (same component as Now),
  favourite toggle.

Acceptance:
- [ ] Search tests: code "970", Georgian name, transliterated name, typo tolerance.
- [ ] Maestro flow: search, open stop, favourite it, it appears on Now.

## [ ] T09 Location and nearby stops
Depends: T08
- Runtime location permission flow (rationale, denied, permanently denied), fused
  location, stops within 500 m sorted by walking time estimate, each expandable to its
  departures. Uses location only while the screen is visible.

Acceptance:
- [ ] ViewModel tests for every permission state; emulator check with a mocked location near 1:970.

## [ ] T10 Prediction engine v2: live ETAs mid-route
Depends: T05
- Project GPS fixes onto the pattern polyline; distance to the target stop along the
  route; ETA from a default speed profile by hour, blended with the timetable offset.
- Layover propagation: a bus waiting at a terminus has ETA = its predicted departure +
  travel time to the stop.
- Outlier filter for the board and `arrivalDelay`; confidence (`±1`, `±4`, `timetable only`).

Acceptance:
- [ ] Replay test: a recorded positions trace for one route produces ETAs within a stated error against the actual pass times in the trace.
- [ ] A waiting bus at the terminus yields a downstream ETA after its departure time, never earlier.

## [ ] T11 Map with live buses
Depends: T04, T06, T10
- MapLibre + OpenFreeMap style (light/dark), route polyline, stop pins, live bus markers
  with heading, smoothly animated along the polyline between fixes. Distinct parked
  marker for buses waiting at a terminus with their leave time.
- Tap a stop pin: departures sheet. Your location dot when permission is granted.

Acceptance:
- [ ] Emulator screenshots: route 326 with buses and a parked marker at 1:970.
- [ ] Polling only for routes on screen, stops when the map is not visible (test).

## [ ] T12 Stop to bus, buses coming to my stop
Depends: T07, T11
- Tap any departure row (Now or stop detail): the map opens on that bus, your stop
  pinned, its path to the stop highlighted, departure list in a bottom sheet.
- "Coming to this stop" view: for a stop and a route (or all routes), every bus heading
  to the stop with its ETA label; the route after the stop dimmed.
- Follow a bus: camera follows, sheet lists the stops ahead with predicted times.

Acceptance:
- [ ] Maestro flow: Now, tap 326 row, map shows that bus and stop 1:970, drag sheet, back to list with state kept.
- [ ] Screenshot tests of the sheet states; ViewModel tests for bus selection and follow.

## [ ] T13 Route line diagram
Depends: T12
- A metro-style strip of the route's stops with buses as moving dots and your stop
  marked, shown in the "coming to this stop" sheet.

Acceptance:
- [ ] Screenshot tests with 0, 1, many buses and a parked bus; tap a dot selects that bus on the map.

## [ ] T14 Trip planner
Depends: T04, T06, T10
- From/to: current location, a stop, or an address (geocode, Tbilisi bbox); leave now,
  depart at, arrive by; quick or less walking.
- Itinerary list with leave-home time, legs, transfer tightness; itinerary on the map.

Acceptance:
- [ ] Planner tests from recorded plan fixtures, including the bogus `arrivalDelay` being ignored.
- [ ] Maestro flow: plan home (1:970) to Freedom Square arriving by a time; shows leave-home time.

## [ ] T15 Leave-by widget
Depends: T07
- Glance widget: next leave-by for a chosen saved stop, refreshed by WorkManager plus
  exact minute ticks while it is near departure.

Acceptance:
- [ ] Widget renders on the emulator home screen with live data; screenshot attached to the PR.

## [ ] T16 MVP polish and release build
Depends: T07, T08, T09, T12, T13, T14, T15
- Offline and error states everywhere, empty states, ka translation pass, accessibility
  pass (TalkBack walk-through), app icon, R8 release build that runs, Baseline Profile
  for startup, "unofficial" note in About.

Acceptance:
- [ ] Release APK runs on the emulator and the user's phone.
- [ ] Every Maestro flow passes on the release build.
- [ ] Cold start under 1 s on the S22 (macrobenchmark or `am start -W`).
