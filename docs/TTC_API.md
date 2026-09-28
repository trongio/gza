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
