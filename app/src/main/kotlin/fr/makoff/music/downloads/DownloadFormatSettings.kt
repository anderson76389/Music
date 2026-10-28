package fr.makoff.music.downloads

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import fr.makoff.music.R
import fr.makoff.music.constants.InternalDownloadFormatKey
import fr.makoff.music.constants.ExternalDownloadFormatKey
import fr.makoff.music.ui.component.EnumDialog
import fr.makoff.music.ui.component.Material3SettingsGroup
import fr.makoff.music.ui.component.Material3SettingsItem
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.emptyPreferences
import fr.makoff.music.constants.UnifiedDownloadFormatKey
import fr.makoff.music.utils.dataStore
import fr.makoff.music.utils.safeDataStoreEdit
import kotlinx.coroutines.launch

@Composable
fun DownloadFormatSettings(
    showHelp: Boolean = true,
    additionalItems: List<Material3SettingsItem> = emptyList(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences by context.dataStore.data.collectAsState(initial = emptyPreferences())
    val selected = preferences.selectedDownloadFormat()
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        EnumDialog(
            onDismiss = { editing = false },
            onSelect = { value: DownloadAudioFormat ->
                scope.launch {
                    context.safeDataStoreEdit {
                        it[UnifiedDownloadFormatKey] = value.name
                        it[InternalDownloadFormatKey] = value.name
                        it[ExternalDownloadFormatKey] = value.name
                    }
                }
                editing = false
            },
            title = stringResource(R.string.download_format_unified),
            current = selected,
            values = DownloadAudioFormat.entries.toList(),
            valueText = { stringResource(it.label) },
        )
    }
    Material3SettingsGroup(
        title = stringResource(R.string.download_quality_settings),
        items = listOf(Material3SettingsItem(
            icon = painterResource(R.drawable.download),
            title = { Text(stringResource(R.string.download_format_unified)) },
            description = if (showHelp) { { Text(stringResource(selected.label)) } } else null,
            onClick = { editing = true },
        )) + additionalItems,
    )
    if (showHelp) Text(stringResource(R.string.download_format_unified_help))
}
