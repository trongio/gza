#!/usr/bin/env bash
# Records real TTC gateway responses as test fixtures in core/ttc/src/test/resources/fixtures/,
# with the key stripped. Linux only (GNU date).
#
#   tools/record-fixtures.sh [all|static|live|errors|terminus [minutes]]   (default: all)
#
#   static    stops, routes, route details, schedules, stops of patterns, polylines,
#             geocoding. Overwrites: this data changes weekly at most.
#   live      boards, positions and plans, timestamped and never overwritten, so real odd
#             responses accumulate.
#   errors    the gateway's error shapes (500 for unknown ids, 400 problem JSON, 401).
#   terminus  waits up to [minutes] (default 30) for the layover case: a board row reading 0
#             at stop 1:970 while a bus with no heading is parked there. Polls every 60 s.
#             Tends to work on weekday evenings (seen for 551 at 20:43 and for 326 at about
#             17:11 on 2026-09-28). Exits 3 when nothing was caught.
#   all       static, live and errors (not terminus: it waits).
#
# The key comes from $TTC_GATEWAY_KEY, else ttc.properties, else a fresh
# tools/fetch-ttc-config.sh run. It only ever sits in a 0600 temp file and a curl header
# file: never on a command line, in a URL, in a fixture or in the output. Only response
# bodies are saved, never headers, and a body containing the key aborts the run.
set -euo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
root="$repo/core/ttc/src/test/resources/fixtures"
mode="${1:-all}"

umask 077
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

stop970=1:970
stop970_lat=41.722055
stop970_lon=44.703114
freedom_square=41.694033,44.801559
plan_modes=WALK,SUBWAY,BUS,GONDOLA
bbox=44.6,41.6,45.0,41.85
# About 120 m around 1:970.
near_dlat=0.0011
near_dlon=0.0015

declare -A route_ids=(
	[301]=1:R29981
	[326]=1:R97493
	[551]=1:minibusR24579
	[472]=1:minibusR25521
)
routes=(301 326 551 472)

failed=0

prop() {
	[[ -f $2 ]] && sed -n "s/^${1//./\\.}=//p" "$2" | head -1 | tr -d '[:space:]'
	return 0
}

load_key() {
	local props="$repo/ttc.properties"
	if [[ -n ${TTC_GATEWAY_KEY:-} ]]; then
		printf '%s' "$TTC_GATEWAY_KEY" >"$tmp/key"
		base="${TTC_GATEWAY_BASE_URL:-}"
	else
		if [[ ! -f $props ]]; then
			props="$tmp/ttc.properties"
			"$repo/tools/fetch-ttc-config.sh" "$props" >/dev/null
		fi
		prop ttc.gatewayKey "$props" >"$tmp/key"
		base=$(prop ttc.gatewayBaseUrl "$props")
	fi
	if [[ ! -s $tmp/key ]]; then
		echo "no gateway key found" >&2
		exit 2
	fi
	base="${base:-https://transit.ttc.com.ge/pis-gateway}"
	base="${base%/}"
	{
		printf 'x-api-key: '
		cat "$tmp/key"
		printf '\n'
	} >"$tmp/headers"
	# Deliberately wrong, for the 401 fixture. Never the real key.
	printf 'x-api-key: gza-fixture-wrong-key\n' >"$tmp/wrong-headers"
}

# fetch <api-path-and-query> [header-file]: body into $tmp/body; sets status, ctype, fetched_at.
fetch() {
	local path=$1 headers=${2:-$tmp/headers} out
	fetched_at=$(date -u +%FT%TZ)
	if ! out=$(curl -sS --max-time 30 -H @"$headers" -o "$tmp/body" -w '%{http_code}\t%{content_type}' "$base/api$path"); then
		status=000
		ctype=
		: >"$tmp/body"
		return 0
	fi
	status=${out%%$'\t'*}
	ctype=${out#*$'\t'}
	if grep -qF -f "$tmp/key" "$tmp/body"; then
		rm -f "$tmp/body"
		echo "response contained the key, not saved" >&2
		exit 1
	fi
}

# save <out-relative-path-without-extension> <body-file> <request> <status> <content-type> <recorded-at>
save() {
	local out=$1 body=$2 request=$3 code=$4 type=$5 at=$6 file
	mkdir -p "$(dirname "$root/$out")"
	if [[ $type == *json* ]]; then
		file="$out.json"
		jq 'walk(if type == "object" then del(.["x-api-key"], .apiKey, .PIS_GATEWAY_KEY) else . end)' \
			"$body" >"$root/$file"
	else
		file="$out.txt"
		cp "$body" "$root/$file"
	fi
	jq -n \
		--arg request "$request" \
		--argjson status "$code" \
		--arg contentType "$type" \
		--arg recordedAt "$at" \
		--arg recordedAtTbilisi "$(TZ=Asia/Tbilisi date -d "$at" +%FT%T%:z)" \
		'{request: $request, status: $status, contentType: $contentType, recordedAt: $recordedAt,
		  recordedAtTbilisi: $recordedAtTbilisi, derived: false}' >"$root/$out.meta.json"
	echo "saved $file"
}

# record <out-relative-path-without-extension> <api-path-and-query> <expected-status> [header-file] [jq-check]
# A wrong status keeps the old fixture (a 502 must not replace a good one) and fails the run at the end.
# A body failing [jq-check] (say an "empty" case that is not empty today) keeps the old fixture
# with a warning: the gateway is fine, it just is not showing that case right now.
record() {
	local out=$1 path=$2 expected=$3 check=${5:-}
	fetch "$path" "${4:-$tmp/headers}"
	if [[ $status != "$expected" ]]; then
		echo "WARNING: $path gave $status, expected $expected; kept $out" >&2
		failed=1
	elif [[ -n $check ]] && ! jq -e "$check" "$tmp/body" >/dev/null 2>&1; then
		echo "WARNING: $path does not match $check today; kept $out" >&2
	else
		save "$out" "$tmp/body" "$path" "$status" "$ctype" "$fetched_at"
	fi
	sleep 1
}

# Like record, but never replaces an existing fixture.
record_new() {
	if compgen -G "$root/$1.*" >/dev/null; then
		echo "exists, skipped: $1"
		return 0
	fi
	record "$@"
}

safe() { printf '%s' "${1//:/-}"; }

uri() { jq -rn --arg v "$1" '$v | @uri'; }

# Pattern suffixes of a route, read from its recorded detail (fetched into tmp if missing).
patterns_of() {
	local name=$1 detail="$root/route/$1-en.json"
	if [[ ! -f $detail ]]; then
		fetch "/v3/routes/${route_ids[$name]}?locale=en"
		detail="$tmp/detail-$name.json"
		cp "$tmp/body" "$detail"
	fi
	jq -r '.patterns[]?.patternSuffix // empty' "$detail"
}

joined_patterns_of() { patterns_of "$1" | paste -sd, -; }

static() {
	local name id p
	record stops/all-en "/v2/stops?locale=en" 200
	record stop/1-970-en "/v2/stops/$stop970?locale=en" 200
	record stop/1-970-ka "/v2/stops/$stop970?locale=ka" 200
	record stop-routes/1-970-en "/v2/stops/$stop970/routes?locale=en" 200
	record routes/all-en "/v3/routes?modes=BUS,SUBWAY,GONDOLA&locale=en" 200
	for name in "${routes[@]}"; do
		record "route/$name-en" "/v3/routes/${route_ids[$name]}?locale=en" 200
	done
	record route/551-ka "/v3/routes/${route_ids[551]}?locale=ka" 200
	record route/metro-1-en "/v3/routes/1:Metro_Metro_1?locale=en" 200
	for name in "${routes[@]}"; do
		id=${route_ids[$name]}
		for p in $(patterns_of "$name"); do
			record "schedule/$name-$(safe "$p")-en" "/v3/routes/$id/schedule?patternSuffix=$p&locale=en" 200
			record "stops-of-patterns/$name-$(safe "$p")-en" \
				"/v3/routes/$id/stops-of-patterns?patternSuffixes=$p&locale=en" 200
		done
		record "polylines/$name" "/v3/routes/$id/polylines?patternSuffixes=$(joined_patterns_of "$name")" 200
	done
	record stops-of-patterns/326-both-en "/v3/routes/${route_ids[326]}/stops-of-patterns?patternSuffixes=0:01,1:01&locale=en" 200
	record geocode/rustaveli-en "/v2/geocode?query=rustaveli&locale=en&bbox=$bbox" 200
	record geocode/rustaveli-ka "/v2/geocode?query=$(uri რუსთაველი)&locale=ka&bbox=$bbox" 200
	record geocode/no-results "/v2/geocode?query=zzqxqzzqxq&locale=en&bbox=$bbox" 200 "$tmp/headers" '.features | length == 0'
	record reverse-geocode/1-970-en "/v2/geocode/reverse?lat=$stop970_lat&lon=$stop970_lon&locale=en" 200
	record reverse-geocode/nowhere "/v2/geocode/reverse?lat=0&lon=0&locale=en" 200 "$tmp/headers" '.features | length == 0'
}

live() {
	local ts name tomorrow from="$stop970_lat,$stop970_lon"
	ts=$(TZ=Asia/Tbilisi date +%Y%m%dT%H%M)
	tomorrow=$(TZ=Asia/Tbilisi date -d tomorrow +%F)
	record_new "arrival-times/1-970-en-$ts" "/v2/stops/$stop970/arrival-times?locale=en&ignoreScheduledArrivalTimes=false" 200
	record_new "arrival-times/1-970-ka-$ts" "/v2/stops/$stop970/arrival-times?locale=ka&ignoreScheduledArrivalTimes=false" 200
	record_new "arrival-times/1-972-en-$ts" "/v2/stops/1:972/arrival-times?locale=en&ignoreScheduledArrivalTimes=false" 200
	record_new "arrival-times/metro-1-1-en-$ts" "/v2/stops/1:metro_1_1/arrival-times?locale=en&ignoreScheduledArrivalTimes=false" 200
	# Answered [] on 2026-09-28 evening; the metro stop above did too once, then sent a row.
	record arrival-times/empty-gondola-5 "/v2/stops/1:gondola_5/arrival-times?locale=en&ignoreScheduledArrivalTimes=false" 200 \
		"$tmp/headers" 'type == "array" and length == 0'
	for name in "${routes[@]}"; do
		record_new "positions/$name-$ts" \
			"/v3/routes/${route_ids[$name]}/positions?patternSuffixes=$(joined_patterns_of "$name")" 200
	done
	record_new "plan/leave-now-970-to-freedom-square-$ts" \
		"/v2/plan?fromPlace=$from&toPlace=$freedom_square&departMode=leaveNow&modes=$plan_modes&optimize=quick&locale=en" 200
	record_new "plan/arrive-by-970-to-freedom-square-$ts" \
		"/v2/plan?fromPlace=$from&toPlace=$freedom_square&departMode=arriveBy&date=$tomorrow&time=09:00&modes=$plan_modes&optimize=lessWalking&locale=en" 200
	record plan/no-itineraries \
		"/v2/plan?fromPlace=$from&toPlace=$from&departMode=leaveNow&modes=$plan_modes&optimize=quick&locale=en" 200 \
		"$tmp/headers" '(.itineraries // []) | length == 0'
}

errors() {
	local from="$stop970_lat,$stop970_lon"
	record errors/stop-unknown-500 "/v2/stops/1:99999999?locale=en" 500
	record errors/schedule-missing-pattern-400 "/v3/routes/${route_ids[326]}/schedule?locale=en" 400
	record errors/geocode-missing-bbox-400 "/v2/geocode?query=rustaveli&locale=en" 400
	record errors/plan-departat-without-date-400 \
		"/v2/plan?fromPlace=$from&toPlace=$freedom_square&departMode=departAt&modes=$plan_modes&optimize=quick&locale=en" 400
	record errors/positions-unknown-pattern "/v3/routes/${route_ids[326]}/positions?patternSuffixes=9:99" 200 \
		"$tmp/headers" 'type == "object" and length == 0'
	record errors/wrong-key-401 "/v2/stops/$stop970?locale=en" 401 "$tmp/wrong-headers"
}

# The pattern of a board row reading 0 for route <name> whose vehicle with no heading sits at 1:970.
layover_pattern() {
	local name=$1 positions=$2
	jq -e --slurpfile pos "$positions" --arg name "$name" \
		--argjson lat "$stop970_lat" --argjson lon "$stop970_lon" \
		--argjson dlat "$near_dlat" --argjson dlon "$near_dlon" -r '
		def near: (.lat - $lat | fabs) < $dlat and (.lon - $lon | fabs) < $dlon;
		[.[]? | select(.shortName == $name and .realtimeArrivalMinutes == 0)] as $rows
		| [$pos[0] | objects | .[]? | .[]? | select(.heading == null and .lat != null and .lon != null and near)] as $parked
		| if ($rows | length) > 0 and ($parked | length) > 0 then $rows[0].patternSuffix else empty end
	' "$tmp/board" 2>/dev/null
}

terminus() {
	local minutes=${1:-30} i name p ts dir board_at pos_at board_request pos_request pos_status pos_type board_type
	for ((i = 0; i < minutes; i++)); do
		board_request="/v2/stops/$stop970/arrival-times?locale=en&ignoreScheduledArrivalTimes=false"
		fetch "$board_request"
		board_at=$fetched_at
		board_type=$ctype
		if [[ $status == 200 ]]; then
			cp "$tmp/body" "$tmp/board"
			for name in 301 326 551; do
				pos_request="/v3/routes/${route_ids[$name]}/positions?patternSuffixes=$(joined_patterns_of "$name")"
				fetch "$pos_request"
				pos_at=$fetched_at
				pos_status=$status
				pos_type=$ctype
				[[ $pos_status == 200 ]] || continue
				cp "$tmp/body" "$tmp/positions"
				if p=$(layover_pattern "$name" "$tmp/positions") && [[ -n $p ]]; then
					ts=$(TZ=Asia/Tbilisi date -d "$board_at" +%Y%m%dT%H%M)
					dir="terminus/$name-$ts"
					save "$dir/arrival-times" "$tmp/board" "$board_request" 200 "$board_type" "$board_at"
					save "$dir/positions" "$tmp/positions" "$pos_request" 200 "$pos_type" "$pos_at"
					record "$dir/schedule-$(safe "$p")" "/v3/routes/${route_ids[$name]}/schedule?patternSuffix=$p&locale=en" 200
					echo "captured $name at $ts"
					return 0
				fi
			done
		fi
		sleep 60
	done
	echo "no parked bus with a 0 board row within $minutes minutes; try a weekday evening" >&2
	exit 3
}

load_key
case "$mode" in
all)
	static
	live
	errors
	;;
static) static ;;
live) live ;;
errors) errors ;;
terminus) terminus "${2:-30}" ;;
*)
	echo "usage: $0 [all|static|live|errors|terminus [minutes]]" >&2
	exit 2
	;;
esac

if [[ $failed -ne 0 ]]; then
	echo "some requests did not return the expected status; their old fixtures were kept" >&2
	exit 1
fi
