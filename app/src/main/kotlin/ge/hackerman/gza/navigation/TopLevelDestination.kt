package ge.hackerman.gza.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ge.hackerman.gza.R
import ge.hackerman.gza.core.designsystem.icon.GzaIcons
import ge.hackerman.gza.feature.map.navigation.MapDestination
import ge.hackerman.gza.feature.now.navigation.NowDestination
import ge.hackerman.gza.feature.plan.navigation.PlanDestination
import ge.hackerman.gza.feature.search.navigation.SearchDestination
import ge.hackerman.gza.feature.settings.navigation.SettingsDestination
import kotlin.reflect.KClass

/** The bottom bar, in order. [testTag] doubles as the Maestro id. */
enum class TopLevelDestination(
    val route: KClass<*>,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
    @StringRes val label: Int,
    val testTag: String
) {
    NOW(NowDestination::class, GzaIcons.Now, GzaIcons.NowSelected, R.string.nav_now, "nav_now"),
    SEARCH(SearchDestination::class, GzaIcons.Search, GzaIcons.Search, R.string.nav_search, "nav_search"),
    MAP(MapDestination::class, GzaIcons.Map, GzaIcons.MapSelected, R.string.nav_map, "nav_map"),
    PLAN(PlanDestination::class, GzaIcons.Plan, GzaIcons.PlanSelected, R.string.nav_plan, "nav_plan"),
    SETTINGS(
        SettingsDestination::class,
        GzaIcons.Settings,
        GzaIcons.SettingsSelected,
        R.string.nav_settings,
        "nav_settings"
    )
}
