# ttc-leave-by prototype (reference only)

The single-stop prototype that proved the approach on a real phone on 2026-09-28:
timetable departures for stop 1:970 plus GPS parked-bus detection. Not built as part of
Gza. T05 ports its logic and test cases into `:core:predict`.

- `Plan.kt`: service period selection, departures across today and tomorrow, parked-bus
  distance check, leave-by plan with live states.
- `PlanTest.kt`: its unit tests.
