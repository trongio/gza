# Gza

Unofficial Tbilisi transit app for Android. Read `PLAN.md` first (scope, architecture,
phases), then `docs/TTC_API.md` (the gateway as observed, with its known lies).

## Rules

- Never commit the TTC gateway key, Firebase config values, or `ttc.properties`. Get
  the key with `tools/fetch-ttc-config.sh`; the app fetches it at runtime.
- The arrivals board and `arrivalDelay` are hints, never the truth. GPS positions plus
  the timetable win. A bus parked at a terminus is waiting, not arriving.
- Keep `:core:ttc` and `:core:predict` free of `android.*` imports so they stay plain
  JUnit tests. Real responses go in as fixtures, including the bad ones.
- Poll only what is on screen. Be polite to TTC's servers.
- No TTC name or logo in the app title or icon. Always say unofficial.
- Never use the em dash character anywhere.

## Build environment

Same as `../gree-local`: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`,
`ANDROID_HOME=~/Android/Sdk`, phone `adb -s R5CW61C9K4F`.
