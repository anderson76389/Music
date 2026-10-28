package fr.makoff.music.downloads

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.emptyPreferences
import androidx.documentfile.provider.DocumentFile
import fr.makoff.music.R
import fr.makoff.music.constants.FolderAudioTreeKey
import fr.makoff.music.ui.component.DefaultDialog
import fr.makoff.music.utils.dataStore
import fr.makoff.music.utils.safeDataStoreEdit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun rememberAudioDownloadAction(onDownload: (Uri, DownloadAudioFormat) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences by context.dataStore.data.collectAsState(initial = emptyPreferences())
    var choosing by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(DownloadAudioFormat.BEST) }
    var pending by rememberSaveable { mutableStateOf(DownloadAudioFormat.BEST) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { persistFolder(context, uri) }
                if (context.safeDataStoreEdit { it[FolderAudioTreeKey] = uri.toString() }) onDownload(uri, pending)
                else Toast.makeText(context, R.string.folder_audio_access_error, Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(context, R.string.folder_audio_access_error, Toast.LENGTH_LONG).show()
            }
        }
    }
    if (choosing) {
        DefaultDialog(
            onDismiss = { choosing = false },
            title = { Text(stringResource(R.string.download_choose_quality)) },
            buttons = {
                TextButton(onClick = { choosing = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = {
                    val format = selected
                    pending = format
                    choosing = false
                    val uri = preferences[FolderAudioTreeKey]?.takeIf(String::isNotBlank)?.let(Uri::parse)
                    scope.launch {
                        val permitted = withContext(Dispatchers.IO) {
                            uri != null && runCatching { DocumentFile.fromTreeUri(context, uri)?.canWrite() == true }.getOrDefault(false)
                        }
                        if (permitted) onDownload(requireNotNull(uri), format) else picker.launch(uri)
                    }
                }) { Text(stringResource(R.string.action_download)) }
            },
        ) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup()) {
                DownloadAudioFormat.entries.forEach { format ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = selected == format,
                            role = Role.RadioButton, onClick = { selected = format }).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == format, onClick = null)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(stringResource(format.label))
                            Text(stringResource(when (format) {
                                DownloadAudioFormat.BEST -> R.string.download_choice_best_help
                                DownloadAudioFormat.M4A -> R.string.download_choice_m4a_help
                                DownloadAudioFormat.FLAC -> R.string.download_choice_flac_help
                            }), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    return {
        selected = preferences.selectedDownloadFormat()
        choosing = true
    }
}
