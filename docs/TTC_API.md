# TTC gateway API (reverse-engineered)

Observed from transit.ttc.com.ge on 2026-09-28 with Playwright and curl. Unofficial and
undocumented: it can change without notice. Every claim below was checked against a live
response.

## Access

- Base: `https://transit.ttc.com.ge/pis-gateway/api` (Remote Config `PIS_GATEWAY_BASE_URL`)
- Header: `x-api-key: <PIS_GATEWAY_KEY>`. The web app reads the key from Firebase Remote
  Config (project `tbilisi-transit-production`), so it can rotate. Run
  `tools/fetch-ttc-config.sh` to get the current one. **Never commit it.**
- Auth failures (checked 2026-09-28 with curl): a wrong key gets `401` with the plain
  text body `Unauthorized` (no content type, no `WWW-Authenticate`). A missing or empty
  `x-api-key` header gets `400` `Bad Request`, not 401. An unknown path with a good key
  gets `404`. No `403` has been seen, but Gza treats 401 and 403 alike.
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
| `GET /v2/stops/{stopId}/routes?locale=` | Routes serving it: `{id, shortName, longName, color, mode}` |
| `GET /v2/stops/{stopId}/arrival-times?locale=&ignoreScheduledArrivalTimes=false` | Board: `[{shortName, color, headsign, patternSuffix, vehicleMode, realtime, realtimeArrivalMinutes, scheduledArrivalMinutes}]`, one entry per route |

**Board caveat:** at a terminus, `realtimeArrivalMinutes` counts a bus *arriving* there,
and reads `0` while a bus rests for its next departure. Observed at stop `1:970`, 17:11:
route 326 reported `0` with a bus parked and the next timetable departure at 17:49.
`scheduledArrivalMinutes` can be large negatives (`-90`, `-100`) for trips that never ran.

Rechecked live on 2026-09-28 at 20:43 (T03 fixtures): the board at `1:970` read `551: 0`
(`scheduledArrivalMinutes: -76`) while minibus `1:981` sat on pattern `0:01` at exactly
the stop's coordinates (`41.7220535, 44.7031136`) with `heading` and `nextStopId` null;
the next timetable departure was 20:47. `326` read `-30` scheduled.
- Board rows have **no route id**: only `shortName`, `color`, `headsign`, `patternSuffix`,
  `vehicleMode`. Join them to routes by `shortName` (and `patternSuffix`) through
  `/v2/stops/{id}/routes`.
- `ignoreScheduledArrivalTimes=true` returned the same rows at `1:970`.
- A stop with no service (metro stop `1:metro_1_1`) returns `[]`. `locale=ka` translates
  `headsign`.
- `realtime` was `true` on every row seen so far; treat `realtimeArrivalMinutes` as
  nullable anyway.
- Stops: 29 of 2,753 have `code: null` (all metro and cable car stops).

## Routes (v3)

| Endpoint | Returns |
|---|---|
| `GET /v3/routes?modes=BUS,SUBWAY,GONDOLA&locale=` | All routes (280): `{id, shortName, longName, color, mode}` |
| `GET /v3/routes/{routeId}?locale=[&forEntireCurrentWeek=true]` | Route with `patterns[]`: `{patternSuffix, directionId, firstStop{id,name}, lastStop{id,name}, headsign}` |
| `GET /v3/routes/{routeId}/schedule?patternSuffix=0:01&locale=` | Timetable, below |
| `GET /v3/routes/{routeId}/stops-of-patterns?patternSuffixes=0:01,1:01&locale=` | Ordered stops per pattern |
| `GET /v3/routes/{routeId}/polylines?patternSuffixes=0:01` | Encoded polyline per pattern |
| `GET /v3/routes/{routeId}/positions?patternSuffixes=0:01,1:01` | Live vehicles, below |

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
- Times past midnight go beyond 24: `24:05`, `24:08` (route 326). They belong to the
  service day they are listed under.
- Route 551 has three periods: `MONDAY-FRIDAY`, `SATURDAY-SATURDAY`, `SUNDAY-SUNDAY`, each
  with only this week's dates (`2026-10-03`, `2026-10-04`).
- `patternSuffix` is required (missing: `400` problem JSON); an unknown one (`9:99`) gives
  `500` `text/plain` `An unexpected error has occurred`.

### Positions
```json
{"0:01":[{"vehicleId":"1:3046","lat":41.7220535,"lon":44.7031136,"heading":null,"nextStopId":null}],
 "1:01":[{"vehicleId":"1:3298","lat":41.7172127,"lon":44.7789268,"heading":1.88,"nextStopId":"1:926"}]}
```
- Buses resting at a terminus report the stop's own coordinates with `heading` and
  `nextStopId` both `null`. Several can be parked at once (seen: 3 on route 551 at `1:970`).
- A bus at the last stop of one pattern usually reappears on the reverse pattern.
- `heading` and `nextStopId` are independent: route 551 vehicle `1:220` had a heading but
  `nextStopId: null` mid-route. Vehicles at the far terminus of `1:01` also report both null.
- `heading` is a float in degrees (`331.5845031738281`).
- `patternSuffixes` is required (missing: `400`); an unknown suffix returns `{}`.

### Route detail
`GET /v3/routes/{routeId}` (checked 2026-09-28 for 301, 326, 551, 472, metro 1):
`{id, shortName, color, mode, patterns[], defaultPatternSuffix}`. `longName` is absent
(null). `forEntireCurrentWeek=true` returned the identical body for 326.
- Patterns are not always `0:01`/`1:01`: route 472 (`1:minibusR25521`) has `0:03` and
  `1:03`, default `1:03`. Always read the suffixes from the route detail.
- Minibus `headsign` stays Georgian with `locale=en` (472: `ლობჟანიძის ქ.`).
- Unknown route id: `500` `text/plain`.

### Stops of patterns
**Not ordered per pattern when you ask for several.** With `patternSuffixes=0:01,1:01`
the response is one merged, de-duplicated list `[{stop:{id,code,name,lat,lon,vehicleMode},
patternSuffixes:["0:01","1:01"]}]` (82 entries for 326); filtering it by suffix does
**not** give the travel order. With a single suffix (`patternSuffixes=0:01`) the list is
in travel order (46 stops, `1:970` first, `1:824` last) and matches the schedule's
`position` order. Request one pattern per call.

### Polylines
`{"0:01":{"color":"00B38B","encodedValue":"..."}}`: Google encoded polyline, precision 5
(1e5). Decoded endpoints of 326 sit on the termini (`41.72205, 44.70311` for `1:970`).

## Planner (v2)

`GET /v2/plan?fromPlace=lat,lon&toPlace=lat,lon&departMode=leaveNow&modes=WALK,SUBWAY,BUS,GONDOLA&optimize=quick&locale=`

- `departMode`: `leaveNow`, `departAt`, `arriveBy`; the last two take `date=YYYY-MM-DD&time=HH:mm`.
- `optimize`: `quick`, `lessWalking`.
- Response is OpenTripPlanner-shaped: `{from, to, itineraries[]}`. An itinerary has
  `startTime, endTime, duration, walkTime, walkDistance, legs[]`. A leg has
  `mode, from, to, startTime, endTime, realTime, arrivalDelay, distance, duration,
  route{shortName,longName,color}, intermediateStops[], legPolyline, steps[]`.
- **`arrivalDelay` is unreliable:** seen `3676` and `3030` seconds on legs that were
  roughly on time. Treat it as a hint. Again on 2026-09-28 20:43: `4607`, `4609` and
  **negative** `-3631`, `-1039`, `-254` seconds; WALK legs report `0`.
- Times look like `2026-09-28T16:43:46.000+00:00` (offset, not `Z`).
- `from`/`to` of an itinerary and of every leg are only `{lat, lon, name}`: **no stop id**.
  `intermediateStops[]` are full stop objects `{id, code, name, lat, lon, vehicleMode}`.
- Leg `route` is `{id, shortName, longName, color, mode}` with `id` and `mode` **null**;
  `route` itself is null on WALK legs.
- `legPolyline` is `{color, encodedValue}` (color null, precision 5, like `/polylines`).
- `steps[]` (WALK legs only): `{relativeDirection, distance, streetName, lat, lon}`.
- `duration`, `walkTime` are seconds; `distance`, `walkDistance` metres (decimals).
- No route found (same from and to, or outside Tbilisi): `200` with `itineraries: []`.
- `departAt` without `date`/`time`: `400` **`text/plain`** (`'date' and 'time' must be
  nonempty if depart mode isn't LEAVE_NOW`), not problem JSON.

## Geocoding (v2)

| Endpoint | Notes |
|---|---|
| `GET /v2/geocode?query=&locale=&bbox=` | `bbox` is required (the web app passes its Tbilisi profile bbox) |
| `GET /v2/geocode/reverse?lat=&lon=&locale=` | GeoJSON `features[]` with Photon-style `properties` (street, city, postcode, osm ids) |

Checked 2026-09-28:
- Both return a GeoJSON `FeatureCollection` `{type, features[]}`. Each feature is
  `{type:"Feature", geometry:{type:"Point", coordinates:[lon, lat]}, properties:{...}}`:
  **longitude first**.
- `properties` seen: `name, street, housenumber, locality, district, city, postcode,
  country, countrycode, osm_id, osm_type, osm_key, osm_value, type, extent`. All optional.
  `extent` is Photon order `[minLon, maxLat, maxLon, minLat]`.
- `bbox` order is `minLon,minLat,maxLon,maxLat`: `44.6,41.6,45.0,41.85` gives results,
  the lat/lon swapped box gives none. Missing `bbox`: `400` problem JSON.
- No match (or reverse far outside Georgia): `200` `{"features":[],"type":"FeatureCollection"}`.
- `locale=ka` with a Georgian query works (`რუსთაველი`).

## Errors and encoding (checked 2026-09-28)

- Unknown stop id, route id or pattern suffix: **`500`** `text/plain;charset=UTF-8` body
  `An unexpected error has occurred`, not 404. A 500 can therefore mean "no such id".
- Missing required query parameter: `400` `application/problem+json`
  `{type, title, status, detail, instance}`. The planner's date check is the exception
  (`400` `text/plain`).
- Percent-encoded ids and lists are accepted: `1%3A970`, `patternSuffixes=0%3A01%2C1%3A01`
  and repeated `patternSuffixes=0:01&patternSuffixes=1:01` all give the same response as
  the raw form, so Retrofit's default query encoding is fine.
- Every success response is `application/json`. Sizes: all stops ~325 KB, all routes
  ~42 KB, a busy route's schedule for one pattern 30 to 80 KB.

## Remote Config (runtime key)

How the web app (and `tools/fetch-ttc-config.sh`, and Gza at runtime) gets the key.
Checked live on 2026-09-28. The Firebase web API key, project id and app id come from the
`firebaseCredentials` object embedded in the transit.ttc.com.ge page; Gza bakes them into
`BuildConfig` from `ttc.properties`. Project `tbilisi-transit-production`.

1. **Installations** `POST https://firebaseinstallations.googleapis.com/v1/projects/{projectId}/installations`
   - Headers: `x-goog-api-key: <firebaseApiKey>`, `Content-Type: application/json`.
   - Body: `{"fid":"<22 char id>","appId":"<appId>","authVersion":"FIS_v2","sdkVersion":"w:0.6.4"}`.
   - `200`: `{"name":"projects/.../installations/<fid>","fid":"<fid>","refreshToken":"<112 chars>","authToken":{"token":"<~315 chars>","expiresIn":"604800s"}}`.
     A well-formed fid (22 url-safe base64 chars, first char `c`..`f`) is echoed back; a
     malformed one (`"bad"`) is replaced by a server-assigned fid, still `200`. Always use
     the returned `fid`.
   - Wrong API key: `400` `{"error":{"code":400,"status":"INVALID_ARGUMENT"}}`.
2. **Auth token refresh** `POST .../installations/{fid}/authTokens:generate`
   - Headers: `x-goog-api-key`, `Authorization: FIS_v2 <refreshToken>`.
   - Body: `{"installation":{"sdkVersion":"w:0.6.4","appId":"<appId>"}}`.
   - `200` `{"token":"...","expiresIn":"604800s"}` (7 days). Bad refresh token: `401` `UNAUTHENTICATED`.
   - `DELETE .../installations/{fid}` with the same headers removes an installation (`200`).
3. **Fetch** `POST https://firebaseremoteconfig.googleapis.com/v1/projects/{projectId}/namespaces/firebase:fetch`
   - API key as `?key=<firebaseApiKey>` (what the script does) **or** as the
     `x-goog-api-key` header: both return `200`. Prefer the header so the key never sits in a URL.
   - Body: `{"appInstanceId":"<fid>","appInstanceIdToken":"<authToken>","appId":"<appId>","sdkVersion":"0.4.0","languageCode":"en-US"}`.
   - `200`: `{"entries":{...},"state":"UPDATE","templateVersion":"33"}`. Entries are all
     strings; 47 keys, among them `PIS_GATEWAY_BASE_URL` (`https://transit.ttc.com.ge/pis-gateway`,
     no trailing slash, no `/api`), `PIS_GATEWAY_KEY` (36 chars, matched `ttc.properties`),
     `OTS_GATEWAY_*`, `MAPBOX_*` and large HTML blobs (`*_HTML_CONTENT_*`, up to ~7 KB each).
     The body therefore carries the key: never log it.
   - **The installation token is not enforced today:** a bogus `appInstanceIdToken`, or none
     at all, still returns `200` with the full entries. Do not rely on that staying true.
   - Wrong API key: `400` `INVALID_ARGUMENT` "API key not valid". App id from another
     project: `403` `PERMISSION_DENIED`. Errors use the Google envelope
     `{"error":{"code":...,"status":"...","message":"..."}}`.
   - Other documented `state` values (`NO_TEMPLATE`, `EMPTY_CONFIG`, `NO_CHANGE`) come without
     `entries`; treat them as "no key".

## Out of scope

Remote Config also carries `OTS_GATEWAY_*` (online ticketing). Gza does not touch
ticketing or payments.
