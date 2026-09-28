package ge.hackerman.gza.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import ge.hackerman.gza.core.designsystem.theme.GzaTheme

/** A transparent top bar with the screen title in the display face. Applies status bar insets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GzaTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        modifier = modifier,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

@PreviewLightDark
@Composable
private fun GzaTopAppBarPreview() {
    GzaTheme {
        GzaTopAppBar(title = "Home", subtitle = "Ana Politkovskaia Street, 4 min walk")
    }
}
