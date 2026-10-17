package fr.makoff.music.downloads

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.work.WorkInfo
import androidx.work.WorkManager
import fr.makoff.music.R
import fr.makoff.music.constants.FolderAudioTreeKey
import fr.makoff.music.models.MediaMetadata
import fr.makoff.music.ui.component.Material3MenuItemData
import fr.makoff.music.ui.component.Material3SettingsGroup
import fr.makoff.music.ui.component.Material3SettingsItem
import fr.makoff.music.utils.rememberPreference
import fr.makoff.music.utils.safeDataStoreEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun persistFolder(context: android.content.Context, uri: Uri) {
    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    check(DocumentFile.fromTreeUri(context, uri)?.canWrite() == true)
}

@Composable
fun rememberFolderAudioItem(media: MediaMetadata): Material3MenuItemData {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (tree) = rememberPreference(FolderAudioTreeKey, "")
    fun enqueue(uri: Uri) {
        runCatching { FolderAudioDownloads.enqueue(context, media, uri) }
            .onSuccess { Toast.makeText(context, R.string.folder_audio_queued, Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, R.string.folder_audio_invalid, Toast.LENGTH_LONG).show() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { persistFolder(context, uri) }
                if (context.safeDataStoreEdit { it[FolderAudioTreeKey] = uri.toString() }) enqueue(uri)
                else Toast.makeText(context, R.string.folder_audio_access_error, Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(context, R.string.folder_audio_access_error, Toast.LENGTH_LONG).show()
            }
        }
    }
    return Material3MenuItemData(
        title = { Text(stringResource(R.string.folder_audio_action)) },
        description = { Text(stringResource(R.string.folder_audio_description)) },
        icon = { Icon(painterResource(R.drawable.download), contentDescription = null) },
        onClick = {
            val uri = tree.takeIf(String::isNotBlank)?.let(Uri::parse)
            val permitted = uri != null && runCatching { DocumentFile.fromTreeUri(context, uri)?.canWrite() == true }.getOrDefault(false)
            if (permitted) enqueue(requireNotNull(uri)) else picker.launch(uri)
        },
    )
}

@Composable
fun FolderAudioSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (tree) = rememberPreference(FolderAudioTreeKey, "")
    val manager = remember { WorkManager.getInstance(context) }
    var jobs by remember { mutableStateOf<List<WorkInfo>>(emptyList()) }
    LaunchedEffect(manager) {
        while (isActive) {
            jobs = withContext(Dispatchers.IO) { manager.getWorkInfosByTag(FolderAudioDownloads.TAG).get() }
            delay(1500)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { persistFolder(context, uri) }
                if (!context.safeDataStoreEdit { it[FolderAudioTreeKey] = uri.toString() }) {
                    Toast.makeText(context, R.string.folder_audio_access_error, Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, R.string.folder_audio_access_error, Toast.LENGTH_LONG).show()
            }
        }
    }
    Material3SettingsGroup(
        title = stringResource(R.string.folder_audio_settings),
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.storage),
                title = { Text(stringResource(R.string.folder_audio_choose)) },
                description = { Text(if (tree.isBlank()) stringResource(R.string.folder_audio_choose_hint) else stringResource(R.string.folder_audio_ready)) },
                onClick = { picker.launch(tree.takeIf(String::isNotBlank)?.let(Uri::parse)) },
            ),
        ),
    )
    Text(stringResource(R.string.folder_audio_native), modifier = Modifier.padding(12.dp))
    if (jobs.isNotEmpty()) Text(stringResource(R.string.folder_audio_recent), modifier = Modifier.padding(12.dp))
    jobs.sortedByDescending { work -> work.tags.firstOrNull { it.startsWith("audio-created:") }?.substringAfter(':')?.toLongOrNull() ?: 0L }.take(15).forEach { work ->
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(work.tags.firstOrNull { it.startsWith(FolderAudioDownloads.TITLE_TAG) }?.removePrefix(FolderAudioDownloads.TITLE_TAG).orEmpty())
            val status = when (work.state) {
                WorkInfo.State.SUCCEEDED -> R.string.folder_audio_completed
                WorkInfo.State.FAILED -> if (work.outputData.getString("error") == "storage") R.string.folder_audio_write_error else R.string.folder_audio_failed
                WorkInfo.State.CANCELLED -> R.string.folder_audio_cancelled
                WorkInfo.State.RUNNING -> when {
                    work.progress.getBoolean("waiting", false) -> R.string.folder_audio_waiting
                    work.progress.getBoolean("copying", false) -> R.string.folder_audio_copying
                    else -> R.string.folder_audio_running
                }
                else -> R.string.folder_audio_waiting
            }
            Text(stringResource(status))
            if (!work.state.isFinished) {
                val percent = work.progress.getInt("percent", -1)
                if (percent >= 0) LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { manager.cancelWorkById(work.id) }) { Text(stringResource(R.string.folder_audio_cancel)) }
            } else if (work.state == WorkInfo.State.SUCCEEDED) {
                TextButton(onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(Uri.parse(requireNotNull(work.outputData.getString("uri"))), work.outputData.getString("mime"))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.folder_audio_open_error, Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.folder_audio_open)) }
            }
        }
    }
}
