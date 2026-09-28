// One route key plus its graph functions, named after the feature like every XNavigation.kt.
@file:Suppress("MatchingDeclarationName")

package ge.hackerman.gza.feature.search.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ge.hackerman.gza.feature.search.SearchScreen
import kotlinx.serialization.Serializable

@Serializable
data object SearchDestination

fun NavController.navigateToSearch(navOptions: NavOptions? = null) = navigate(SearchDestination, navOptions)

fun NavGraphBuilder.searchScreen() {
    composable<SearchDestination> {
        // Hoisted here, not in the screen: saved with the tab's back stack entry, so the
        // text survives switching tabs. T08 replaces this with a ViewModel.
        var query by rememberSaveable { mutableStateOf("") }
        SearchScreen(query = query, onQueryChange = { query = it })
    }
}
