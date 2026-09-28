package ge.hackerman.gza.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.R
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import kotlinx.coroutines.launch

/**
 * The map-with-a-sheet layout (PLAN 2.7): [content] fills the screen and the sheet peeks
 * from the bottom. Material 1.4's own drag handle slot wraps the handle in a tooltip box and
 * a click-to-toggle box, so TalkBack stops on it twice and gets no expand or collapse
 * action. The slot is left empty and [GzaSheetDragHandle] leads the sheet content instead:
 * one node that toggles on a double tap and offers expand or collapse. Dragging still works
 * because Material makes the whole sheet draggable. Callers opt in to
 * ExperimentalMaterial3Api because [scaffoldState] is Material's experimental state type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GzaBottomSheetScaffold(
    sheetContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    scaffoldState: BottomSheetScaffoldState = rememberBottomSheetScaffoldState(),
    sheetPeekHeight: Dp = 168.dp,
    dragHandleModifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    BottomSheetScaffold(
        sheetContent = {
            GzaSheetDragHandle(scaffoldState.bottomSheetState, dragHandleModifier)
            sheetContent()
        },
        modifier = modifier,
        scaffoldState = scaffoldState,
        sheetPeekHeight = sheetPeekHeight,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetShadowElevation = 8.dp,
        sheetDragHandle = null,
        content = content
    )
}

/** A full-width 48dp target around a small pill, so the handle is easy to grab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GzaSheetDragHandle(sheetState: SheetState, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.sheet_drag_handle)
    val scope = rememberCoroutineScope()
    val expanded = sheetState.currentValue == SheetValue.Expanded
    val toggle = {
        scope.launch { if (expanded) sheetState.partialExpand() else sheetState.expand() }
        Unit
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(role = Role.Button, onClick = toggle)
            .semantics {
                contentDescription = description
                if (expanded) {
                    collapse {
                        toggle()
                        true
                    }
                } else {
                    expand {
                        toggle()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
        )
    }
}

@Composable
fun GzaSheetHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun GzaBottomSheetScaffoldPreview() {
    GzaTheme {
        GzaBottomSheetScaffold(sheetContent = { GzaSheetHeader("Departures", subtitle = "Freedom Square") }) {
            Box(Modifier.fillMaxSize().background(GzaTheme.colors.mapLand))
        }
    }
}
