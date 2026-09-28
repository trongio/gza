package ge.hackerman.gza.core.designsystem.icon

import androidx.annotation.DrawableRes
import ge.hackerman.gza.core.designsystem.R
import ge.hackerman.gza.core.designsystem.component.TransitMode

/**
 * Material Symbols Rounded as vector drawables (tools/fetch-icons.sh). Drawables, not
 * ImageVector code, because the path strings are far longer than the line limit.
 */
object GzaIcons {
    @DrawableRes val Now = R.drawable.ic_nav_now

    @DrawableRes val NowSelected = R.drawable.ic_nav_now_selected

    @DrawableRes val Search = R.drawable.ic_nav_search

    @DrawableRes val Map = R.drawable.ic_nav_map

    @DrawableRes val MapSelected = R.drawable.ic_nav_map_selected

    @DrawableRes val Plan = R.drawable.ic_nav_plan

    @DrawableRes val PlanSelected = R.drawable.ic_nav_plan_selected

    @DrawableRes val Settings = R.drawable.ic_nav_settings

    @DrawableRes val SettingsSelected = R.drawable.ic_nav_settings_selected

    @DrawableRes val Bus = R.drawable.ic_mode_bus

    @DrawableRes val Minibus = R.drawable.ic_mode_minibus

    @DrawableRes val Metro = R.drawable.ic_mode_metro

    @DrawableRes val CableCar = R.drawable.ic_mode_cable_car

    @DrawableRes val Waiting = R.drawable.ic_status_waiting

    @DrawableRes val Late = R.drawable.ic_status_late

    @DrawableRes val Timetable = R.drawable.ic_status_timetable

    @DrawableRes val Info = R.drawable.ic_info

    @DrawableRes val Offline = R.drawable.ic_offline

    @DrawableRes val Walk = R.drawable.ic_walk
}

/** The glyph that names the kind without colour (colour-blind users, metro 1 vs cable car 1). */
@DrawableRes
fun TransitMode.icon(): Int = when (this) {
    TransitMode.Bus -> GzaIcons.Bus
    TransitMode.Minibus -> GzaIcons.Minibus
    TransitMode.Metro -> GzaIcons.Metro
    TransitMode.CableCar -> GzaIcons.CableCar
}
