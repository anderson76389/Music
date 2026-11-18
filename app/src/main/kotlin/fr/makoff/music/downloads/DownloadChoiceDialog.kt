package fr.makoff.music.downloads

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.emptyPreferences
import androidx.documentfile.provider.DocumentFile
import fr.makoff.music.R
import fr.makoff.music.constants.FolderAudioTreeKey
import fr.makoff.music.utils.dataStore
import fr.makoff.music.utils.safeDataStoreEdit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Directly export the best AAC/M4A track. Don't interrupt downloads with a
 * meaningless quality picker containing only one supported option.
 */
@Composable
internal fun rememberAudioDownloadAction(onDownload: (Uri, DownloadAudioFormat) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences by context.dataStore.data.collectAsState(initial = emptyPreferences())
    val format = DownloadAudioFormat.M4A
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { persistFolder(context, uri) }
                if (context.safeDataStoreEdit { it[FolderAudioTreeKey] = uri.toString() }) {
                    onDownload(uri, format)
                } else {
                    Toast.makeText(context, R.string.folder_audio_access_error, Toast.LENGTH_LONG).show()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(context, R.string.folder_audio_access_error, Toast.LENGTH_LONG).show()
            }
        }
    }
    return {
        val uri = preferences[FolderAudioTreeKey]?.takeIf(String::isNotBlank)?.let(Uri::parse)
        scope.launch {
            val permitted = withContext(Dispatchers.IO) {
                uri != null && runCatching {
                    DocumentFile.fromTreeUri(context, uri)?.canWrite() == true
                }.getOrDefault(false)
            }
            if (permitted) onDownload(requireNotNull(uri), format)
            else picker.launch(uri)
        }
    }
}
