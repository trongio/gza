package ge.hackerman.gza.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.R
import ge.hackerman.gza.core.designsystem.icon.GzaIcons
import ge.hackerman.gza.core.designsystem.theme.GzaTheme

/** A quiet, non-interactive notice above content: sample data now, offline later. */
@Composable
fun GzaBanner(text: String, modifier: Modifier = Modifier, tone: BannerTone = BannerTone.Info) {
    val container = when (tone) {
        BannerTone.Info -> MaterialTheme.colorScheme.secondaryContainer
        BannerTone.Offline -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val icon = when (tone) {
        BannerTone.Info -> GzaIcons.Info
        BannerTone.Offline -> GzaIcons.Offline
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = if (tone == BannerTone.Info) {
            contentColorFor(container)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@PreviewLightDark
@Composable
private fun GzaBannerPreview() {
    GzaTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GzaBanner(stringResource(R.string.sample_data_banner))
            GzaBanner("Offline", tone = BannerTone.Offline)
        }
    }
}
