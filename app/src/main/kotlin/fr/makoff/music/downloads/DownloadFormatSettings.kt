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
import fr.makoff.music.utils.rememberEnumPreference

@Composable
fun DownloadFormatSettings() {
    val (internal, setInternal) = rememberEnumPreference(InternalDownloadFormatKey, DownloadAudioFormat.BEST)
    val (external, setExternal) = rememberEnumPreference(ExternalDownloadFormatKey, DownloadAudioFormat.BEST)
    var editing by remember { mutableStateOf<Int?>(null) }
    editing?.let { target ->
        EnumDialog(
            onDismiss = { editing = null },
            onSelect = { value: DownloadAudioFormat ->
                if (target == 0) setInternal(value) else setExternal(value)
                editing = null
            },
            title = stringResource(if (target == 0) R.string.download_format_internal else R.string.download_format_external),
            current = if (target == 0) internal else external,
            values = DownloadAudioFormat.entries.toList(),
            valueText = { stringResource(it.label) },
        )
    }
    Material3SettingsGroup(
        title = stringResource(R.string.audio_quality),
        items = listOf(internal, external).mapIndexed { index, value ->
            Material3SettingsItem(
                icon = painterResource(R.drawable.download),
                title = { Text(stringResource(if (index == 0) R.string.download_format_internal else R.string.download_format_external)) },
                description = { Text(stringResource(value.label)) },
                onClick = { editing = index },
            )
        },
    )
    Text(stringResource(R.string.download_format_help))
}
