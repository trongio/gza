# Gza

Unofficial Android app for Tbilisi public transport. Answers "when do I leave, and when
will the bus really be here?" on top of TTC's public gateway.

Read in this order before any work:
1. `docs/tasks/BACKLOG.md`: the MVP task list and its status. The single source of truth for what to do next.
2. `PLAN.md`: product scope, architecture, prediction rules.
3. `docs/TTC_API.md`: the gateway as observed, including where it lies.

## How work happens here

The main session is the **manager**. The user runs `/task`, and the manager takes the
next ready task from the backlog and drives it to a merged PR on `main` using the
subagents in `.claude/agents/`:

| Agent | Role | Writes code? |
|---|---|---|
| `planner` | Turns a backlog task into a concrete implementation plan in `docs/tasks/plans/` | Plan file only |
| `implementer` | Implements the plan or fixes findings, commits in small steps | Yes |
| `reviewer` | Reviews the branch diff for bugs, architecture and Android best practice | No |
| `tester` | Writes and runs unit, UI and on-device tests, reports failures | Tests only |

The manager coordinates and verifies. It does not write feature code itself. Full
protocol: `.claude/commands/task.md`.

## Git

- `main` is always green and releasable. Never commit to it directly; only merged PRs land there.
- One branch per task: `task/T07-now-screen`. One PR per task.
- Commit small and often, Conventional Commits (`feat(now): merged departure list`,
  `fix(predict): ...`, `test:`, `build:`, `docs:`, `refactor:`). Push after every
  commit, so the work is never only local.
- Merge with `gh pr merge --merge --delete-branch` (keeps the history of small commits).
- Never force-push `main`. Never rewrite pushed history on a shared branch.
- Never use `--no-verify` or skip CI.

## Build and tools

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=~/Android/Sdk
./tools/fetch-ttc-config.sh      # once per machine: gateway key into ttc.properties (gitignored)
./gradlew assembleDebug
./gradlew test                   # JVM unit tests, all modules
./gradlew lint detekt spotlessCheck
./gradlew spotlessApply          # format
./gradlew connectedDebugAndroidTest   # instrumented, needs a device
~/.maestro/bin/maestro test maestro/  # end-to-end UI flows on a device
```

Devices: emulator `emulator-5554` for automated tests; the user's phone is Galaxy S22
Ultra `R5CW61C9K4F`. Install on the phone only at a task's final check, never uninstall
the user's data on it.

`JAVA_HOME` and `ANDROID_HOME` are also set in `.claude/settings.json`, but the shell may
not export them: prefix Gradle commands with the export above if a build complains.

## Engineering rules

### Architecture (Android best practice, "Now in Android" style)
- Kotlin only, Jetpack Compose only, Material 3. No XML layouts, no Fragments.
- Modules: `:app`, `:core:ttc`, `:core:predict`, `:core:data`, `:core:model`,
  `:core:designsystem`, `:feature:*`, `:widget`. Convention plugins in `build-logic/`.
- `:core:ttc`, `:core:predict`, `:core:model` are **pure Kotlin/JVM**: no `android.*`
  imports, so they run as plain JUnit tests. Keep it that way.
- Unidirectional data flow: ViewModel exposes one `StateFlow<UiState>` built with
  `combine(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ...)`.
  UI sends events as function calls. Screens split into a stateful `XRoute` and a
  stateless `XScreen(uiState, onEvent...)` that previews and tests can drive.
- Collect with `collectAsStateWithLifecycle()`. Never read `StateFlow.value` inside a
  composable: that creates no subscription and the UI silently stops updating.
- Repositories are the single source of truth: Room first, network refreshes Room.
  UI never talks to the network directly.
- DI with Hilt. Coroutines + Flow everywhere; inject dispatchers, never hardcode
  `Dispatchers.IO` in classes you want to test.
- Type-safe Navigation Compose routes (`@Serializable` route objects).
- Networking: OkHttp + Retrofit + kotlinx.serialization. Every response DTO is lenient
  (`ignoreUnknownKeys`, nullable where the gateway has ever sent null).
- Persistence: Room (KSP) for data, DataStore for preferences. WorkManager for sync.
- Time: `java.time` only, zone `Asia/Tbilisi` explicitly. Inject a `Clock` so tests can
  freeze time.
- Map: MapLibre Android with OpenFreeMap vector tiles (free, no key). Never use TTC's
  Mapbox token.
- Strings in `strings.xml` (en default, `values-ka`). No hardcoded user-facing text.
- Edge-to-edge, dark and light themes, dynamic type, TalkBack labels on every
  interactive element, 48dp touch targets.
- Versions in `gradle/libs.versions.toml` only. Use the latest **stable** releases;
  check Maven Central / Google Maven before adding or bumping a dependency.

### TTC gateway
- Never commit the gateway key, Firebase config values or `ttc.properties`. The app
  fetches the key at runtime from TTC's Remote Config; `ttc.properties` is the
  build-time fallback.
- The arrivals board and `arrivalDelay` are hints, never truth. GPS positions plus the
  timetable win. **A bus parked at a terminus is waiting, not arriving**: this is the
  app's headline feature (PLAN.md 2.0) and must never regress.
- Poll only what is on screen; back off on errors; no background polling outside an
  active trip.
- Record real responses as test fixtures with `tools/record-fixtures.sh`, including the
  bad ones. Fixtures must not contain the key.

### Testing
- Every task ships tests. Logic in `:core:*`: JUnit 5 unit tests with fixtures, near
  full coverage of the prediction rules. ViewModels: tests with `kotlinx-coroutines-test`
  and Turbine. Screens: Compose UI tests on the stateless `XScreen`; Roborazzi screenshot
  tests for key states (light, dark, loading, offline, empty).
- User-facing flows get a Maestro flow in `maestro/` once the screen exists.
- CI (GitHub Actions) runs build, unit tests, lint, detekt, spotless on every PR. A red
  CI never merges.

### Style
- Match the surrounding code. Comments explain *why*, not *what*.
- **Never use the em dash character** anywhere: code, comments, docs, commits, UI text.
- No TTC name or logo in the app title or icon; the app always says it is unofficial.
