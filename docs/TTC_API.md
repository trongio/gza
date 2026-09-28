# TTC gateway API (reverse-engineered)

Observed from transit.ttc.com.ge on 2026-09-28 with Playwright and curl. Unofficial and
undocumented: it can change without notice. Every claim below was checked against a live
response.

## Access

- Base: `https://transit.ttc.com.ge/pis-gateway/api` (Remote Config `PIS_GATEWAY_BASE_URL`)
- Header: `x-api-key: <PIS_GATEWAY_KEY>`. The web app reads the key from Firebase Remote
  Config (project `tbilisi-transit-production`), so it can rotate. Run
  `tools/fetch-ttc-config.sh` to get the current one. **Never commit it.**
- **No CORS headers.** Browser pages on other origins can't call it; native apps can.
- Errors come back as RFC 7807 JSON: `{"title":"Bad Request","status":400,"detail":"Required parameter 'departMode' is not present."}`
- `locale`: `ka`, `en`, `ru`. Some fields ignore it (minibus `longName` stays Georgian).
- IDs are feed-prefixed: stop `1:970`, route `1:R97493`, minibus `1:minibusR24579`,
  metro `1:Metro_Metro_1`. Patterns are `<directionId>:<nn>`, e.g. `0:01` and `1:01`.
- Times in `/plan` are ISO UTC. Timetable strings are local `H:mm` (Asia/Tbilisi, UTC+4).

## Stops (v2)

| Endpoint | Returns |
|---|---|
| `GET /v2/stops?locale=` | All stops (2,753): `{id, code, name, lat, lon, vehicleMode}` |
| `GET /v2/stops/{stopId}?locale=` | One stop |
| `GET /v2/stops/{stopId}/routes?locale=` | Routes serving it: `{id, shortName, longName}` |
| `GET /v2/stops/{stopId}/arrival-times?locale=&ignoreScheduledArrivalTimes=false` | Board: `[{shortName, color, headsign, patternSuffix, vehicleMode, realtime, realtimeArrivalMinutes, scheduledArrivalMinutes}]`, one entry per route |

**Board caveat:** at a terminus, `realtimeArrivalMinutes` counts a bus *arriving* there,
and reads `0` while a bus rests for its next departure. Observed at stop `1:970`, 17:11:
route 326 reported `0` with a bus parked and the next timetable departure at 17:49.
`scheduledArrivalMinutes` can be large negatives (`-90`, `-100`) for trips that never ran.

## Routes (v3)

| Endpoint | Returns |
|---|---|
| `GET /v3/routes?modes=BUS,SUBWAY,GONDOLA&locale=` | All routes (280): `{id, shortName, longName, color, mode}` |
| `GET /v3/routes/{routeId}?locale=[&forEntireCurrentWeek=true]` | Route with `patterns[]`: `{patternSuffix, directionId, firstStop{id,name}, lastStop{id,name}, headsign}` |
| `GET /v3/routes/{routeId}/schedule?patternSuffix=0:01&locale=` | Timetable, below |
| `GET /v3/routes/{routeId}/stops-of-patterns?patternSuffixes=0:01,1:01&locale=` | Ordered stops per pattern |
| `GET /v3/routes/{routeId}/polylines?patternSuffixes=0:01` | Encoded polyline per pattern |
| `GET /v3/routes/{routeId}/positions?patternSuffixes=0:01,1:01` | Live vehicles, below |

### Transport kinds and route colours
Checked live on 2026-09-28 (`/v3/routes`, `/v2/stops`, board of `1:970`):

| Kind | How to tell | `color` | Count | `shortName` |
|---|---|---|---|---|
| Bus | `mode: BUS`, id `1:R<n>` | `00B38B` (teal green) | 111 | `101` to `399` |
| Minibus | `mode: BUS`, id `1:minibusR<n>` | `0033B4` (blue) | 164 | `401` to `582` |
| Metro | `mode: SUBWAY`, id `1:Metro_Metro_<line>` | line 1 `ff505b` (red), line 2 `5ca330` (green) | 2 | `1`, `2` |
| Cable car | `mode: GONDOLA`, id `1:Gondola_Gondola_<n>` | `f5861f` (orange) | 3 | `1` to `3` |

- **A minibus is `mode: BUS`**, and the board says `vehicleMode: "BUS"` for it too (route
  551 at `1:970`). Only the route id prefix `minibus` or the colour tells it apart; the
  board has no route id, so there the colour is the only signal.
- `color` is 6 hex digits without `#`, and the case varies (`00B38B` but `f5861f`). Parse
  it case-insensitively and fall back to the kind's colour when it is missing or malformed.
- Every `shortName` is numeric, at most 3 characters. Metro and cable car numbers restart
  at 1, so a badge must show the kind, not only the number.
- Stops: `vehicleMode` is `BUS` (2,724), `SUBWAY` (23) or `GONDOLA` (6). Metro and cable
  car stops have `code: null` and lower-case ids (`1:metro_1_11`, `1:gondola_1`), unlike
  their route ids (`1:Metro_Metro_1`).
- Georgian names use abbreviations: `მ/ს` (metro station), `ქ.` (street), `მ-ნი`
  (square), `გამზ.` (avenue). Example: `1:970` is `Ana Politkovskaia Street` in `en` and
  `ანა პოლიტკოვსკაიას ქუჩა` in `ka`; the 326 headsign is `Baratashvili St` /
  `ბარათაშვილის ქ.`.

### Schedule
```json
[{"fromDay":"MONDAY","toDay":"FRIDAY",
  "serviceDates":["2026-09-28","2026-09-29","2026-09-30","2026-10-01","2026-10-02"],
  "stops":[{"id":"1:970","name":"Ana Politkovskaia Street","position":1,
            "arrivalTimes":"7:55,8:13,8:31,..."}]}]
```
- Periods vary per route: `MONDAY-FRIDAY` + `SATURDAY-SUNDAY`, or separate Saturday and Sunday.
- `serviceDates` covers only the **current week**, so match by weekday once past it.
- At the first stop, `arrivalTimes` are the departure times.

### Positions
```json
{"0:01":[{"vehicleId":"1:3046","lat":41.7220535,"lon":44.7031136,"heading":null,"nextStopId":null}],
 "1:01":[{"vehicleId":"1:3298","lat":41.7172127,"lon":44.7789268,"heading":1.88,"nextStopId":"1:926"}]}
```
- Buses resting at a terminus report the stop's own coordinates with `heading` and
  `nextStopId` both `null`. Several can be parked at once (seen: 3 on route 551 at `1:970`).
- A bus at the last stop of one pattern usually reappears on the reverse pattern.

## Planner (v2)

`GET /v2/plan?fromPlace=lat,lon&toPlace=lat,lon&departMode=leaveNow&modes=WALK,SUBWAY,BUS,GONDOLA&optimize=quick&locale=`

- `departMode`: `leaveNow`, `departAt`, `arriveBy`; the last two take `date=YYYY-MM-DD&time=HH:mm`.
- `optimize`: `quick`, `lessWalking`.
- Response is OpenTripPlanner-shaped: `{from, to, itineraries[]}`. An itinerary has
  `startTime, endTime, duration, walkTime, walkDistance, legs[]`. A leg has
  `mode, from, to, startTime, endTime, realTime, arrivalDelay, distance, duration,
  route{shortName,longName,color}, intermediateStops[], legPolyline, steps[]`.
- **`arrivalDelay` is unreliable:** seen `3676` and `3030` seconds on legs that were
  roughly on time. Treat it as a hint.

## Geocoding (v2)

| Endpoint | Notes |
|---|---|
| `GET /v2/geocode?query=&locale=&bbox=` | `bbox` is required (the web app passes its Tbilisi profile bbox) |
| `GET /v2/geocode/reverse?lat=&lon=&locale=` | GeoJSON `features[]` with Photon-style `properties` (street, city, postcode, osm ids) |

## Out of scope

Remote Config also carries `OTS_GATEWAY_*` (online ticketing). Gza does not touch
ticketing or payments.
