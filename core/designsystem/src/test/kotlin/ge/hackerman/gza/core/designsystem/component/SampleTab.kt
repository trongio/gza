package ge.hackerman.gza.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import ge.hackerman.gza.core.designsystem.icon.GzaIcons

internal data class SampleTab(val label: String, val icon: Int, val selectedIcon: Int)

internal val SampleTabs = listOf(
    SampleTab("Now", GzaIcons.Now, GzaIcons.NowSelected),
    SampleTab("Search", GzaIcons.Search, GzaIcons.Search),
    SampleTab("Map", GzaIcons.Map, GzaIcons.MapSelected),
    SampleTab("Plan", GzaIcons.Plan, GzaIcons.PlanSelected),
    SampleTab("Settings", GzaIcons.Settings, GzaIcons.SettingsSelected)
)

@Composable
internal fun SampleNavigationBar(
    selected: Int,
    labels: List<String> = SampleTabs.map { it.label },
    onClick: (Int) -> Unit = {}
) {
    GzaNavigationBar {
        SampleTabs.forEachIndexed { index, tab ->
            GzaNavigationBarItem(
                selected = index == selected,
                onClick = { onClick(index) },
                icon = tab.icon,
                selectedIcon = tab.selectedIcon,
                label = labels[index],
                modifier = Modifier.testTag("tab_$index")
            )
        }
    }
}
