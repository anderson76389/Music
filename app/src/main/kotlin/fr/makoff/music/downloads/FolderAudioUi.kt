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
        runCatching { FolderAudioDownloads.enqueue(context, media, uri,
            media.album?.let { listOf("Albums", collectionFolder(it.title, it.id)) } ?: listOf("Titres")) }
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
        title = { Text(stringResource(R.string.action_download)) },
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
    DownloadFormatSettings(showHelp = false)
    Material3SettingsGroup(
        title = stringResource(R.string.folder_audio_settings),
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.storage),
                title = { Text(stringResource(R.string.folder_audio_choose)) },
                onClick = { picker.launch(tree.takeIf(String::isNotBlank)?.let(Uri::parse)) },
            ),
        ),
    )
}

@Composable
fun FolderAudioHistory() {
    val context = LocalContext.current
    val manager = remember { WorkManager.getInstance(context) }
    var jobs by remember { mutableStateOf<List<WorkInfo>>(emptyList()) }
    LaunchedEffect(manager) {
        while (isActive) {
            jobs = withContext(Dispatchers.IO) { manager.getWorkInfosByTag(FolderAudioDownloads.TAG).get() }
            delay(1500)
        }
    }
    Text(stringResource(R.string.folder_audio_recent), modifier = Modifier.padding(12.dp))
    if (jobs.isEmpty()) Text(stringResource(R.string.download_history_empty), modifier = Modifier.padding(12.dp))
    jobs.sortedByDescending { work -> work.tags.firstOrNull { it.startsWith("audio-created:") }?.substringAfter(':')?.toLongOrNull() ?: 0L }.take(15).forEach { work ->
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(work.tags.firstOrNull { it.startsWith(FolderAudioDownloads.TITLE_TAG) }?.removePrefix(FolderAudioDownloads.TITLE_TAG).orEmpty())
            val status = when (work.state) {
                WorkInfo.State.SUCCEEDED -> R.string.folder_audio_completed
                WorkInfo.State.FAILED -> when (work.outputData.getString("error")) {
                    "storage" -> R.string.folder_audio_write_error
                    "active" -> R.string.folder_audio_internal_busy
                    else -> R.string.folder_audio_failed
                }
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

/** The exported document is also registered as the app's offline audio source. */
@Composable
fun rememberFolderCollectionItem(kind: String, title: String, id: String,
    load: suspend () -> List<MediaMetadata>): Material3MenuItemData {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (tree) = rememberPreference(FolderAudioTreeKey, "")
    var loading by remember { mutableStateOf(false) }
    fun enqueue(uri: Uri) {
        if (loading) return
        loading = true
        scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) { load() }
                check(tracks.isNotEmpty())
                require(tracks.all { it.id.matches(Regex("[A-Za-z0-9_-]{11}")) })
                val folders = listOf(kind, collectionFolder(title, id))
                withContext(Dispatchers.IO) {
                    tracks.forEachIndexed { index, media -> FolderAudioDownloads.enqueue(context, media, uri, folders, index + 1) }
                }
                Toast.makeText(context, context.getString(R.string.folder_collection_queued, tracks.size), Toast.LENGTH_LONG).show()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(context, R.string.folder_audio_failed, Toast.LENGTH_LONG).show()
            } finally { loading = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { persistFolder(context, uri) }
                if (context.safeDataStoreEdit { it[FolderAudioTreeKey] = uri.toString() }) enqueue(uri)
            } catch (_: Exception) { Toast.makeText(context, R.string.folder_audio_access_error, Toast.LENGTH_LONG).show() }
        }
    }
    return Material3MenuItemData(
        title = { Text(stringResource(if (loading) R.string.folder_audio_waiting else R.string.action_download)) },
        description = { Text(stringResource(R.string.folder_collection_description, kind, title)) },
        icon = { Icon(painterResource(R.drawable.download), contentDescription = null) },
        onClick = {
            if (!loading) {
                val uri = tree.takeIf(String::isNotBlank)?.let(Uri::parse)
                if (uri != null && runCatching { DocumentFile.fromTreeUri(context, uri)?.canWrite() == true }.getOrDefault(false)) enqueue(uri)
                else picker.launch(uri)
            }
        },
    )
}
