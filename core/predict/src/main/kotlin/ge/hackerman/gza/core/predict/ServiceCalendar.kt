package ge.hackerman.gza.core.predict

import ge.hackerman.gza.core.model.ServicePeriod
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The period that runs on [date], or null when nothing runs that day.
 *
 * `serviceDates` only cover the current week (TTC_API.md), so:
 * 1. a period that lists [date] wins (holidays are listed under the period that runs them);
 * 2. a date inside the listed week that no period lists has no service: the gateway said so,
 *    and falling back to the weekday would invent departures;
 * 3. outside the listed week, the period whose weekday range covers the day.
 *
 * Ties (overlapping ranges such as `MONDAY-SATURDAY` plus `SATURDAY-SATURDAY`) go to the
 * period with the fewest weekdays, then to list order.
 */
fun List<ServicePeriod>.periodFor(date: LocalDate): ServicePeriod? {
    val listed = filter { date in it.serviceDates }
    if (listed.isNotEmpty()) return listed.narrowest()
    val allDates = flatMap { it.serviceDates }
    val insideListedWeek = allDates.isNotEmpty() && date >= allDates.min() && date <= allDates.max()
    return if (insideListedWeek) null else filter { it.coversWeekday(date.dayOfWeek) }.narrowest()
}

/** `fromDay..toDay`, wrapping past Sunday when `fromDay` is later (`FRIDAY-MONDAY`). */
internal fun ServicePeriod.coversWeekday(day: DayOfWeek): Boolean = if (fromDay <= toDay) {
    day in fromDay..toDay
} else {
    day >= fromDay || day <= toDay
}

internal fun ServicePeriod.weekdayCount(): Int = if (fromDay <= toDay) {
    toDay.value - fromDay.value + 1
} else {
    DAYS_IN_WEEK - (fromDay.value - toDay.value) + 1
}

// minByOrNull keeps the first of equal elements, which is the list-order tie break.
private fun List<ServicePeriod>.narrowest(): ServicePeriod? = minByOrNull { it.weekdayCount() }

private const val DAYS_IN_WEEK = 7
