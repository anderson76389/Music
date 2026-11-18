package fr.makoff.music.downloads

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import fr.makoff.music.R
import fr.makoff.music.ui.component.Material3SettingsGroup
import fr.makoff.music.ui.component.Material3SettingsItem

/** AAC in M4A is the single portable download mode for new files.
 * There is intentionally no pointless format chooser with one row.
 */
@Composable
fun DownloadFormatSettings(
    showHelp: Boolean = true,
    additionalItems: List<Material3SettingsItem> = emptyList(),
) {
    Material3SettingsGroup(
        title = stringResource(R.string.download_quality_settings),
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.download),
                title = { Text(stringResource(R.string.download_format_unified)) },
                description = {
                    Text(stringResource(
                        if (showHelp) R.string.download_format_unified_help
                        else R.string.download_format_m4a,
                    ))
                },
            )
        ) + additionalItems,
    )
}
