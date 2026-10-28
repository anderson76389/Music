package fr.makoff.music.downloads

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.media3.exoplayer.offline.Download
import androidx.work.WorkInfo
import androidx.work.WorkManager
import fr.makoff.music.playback.DownloadUtil
import fr.makoff.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

internal val HiddenDownloadHistory = stringSetPreferencesKey("missingDownloadHistory")

/** An unavailable provider or revoked permission is not evidence that a file was deleted. */
internal fun documentIsMissing(context: Context, uri: Uri): Boolean = runCatching {
    if (uri.scheme != "content" || !DocumentsContract.isTreeUri(uri)) return false
    val root = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
    val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
    val rootAvailable = context.contentResolver.query(root, projection, null, null, null)?.use { it.moveToFirst() } == true
    if (!rootAvailable) return false
    val row = runCatching { context.contentResolver.query(uri, projection, null, null, null)?.use { it.moveToFirst() } }.getOrNull()
    if (row == false) return true
    if (row == true) return false
    try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { }
        false
    } catch (_: java.io.FileNotFoundException) {
        true
    }

}.getOrDefault(false)

internal suspend fun cleanMissingDownloads(context: Context, downloads: DownloadUtil): Int = withContext(Dispatchers.IO) {
    val manager = WorkManager.getInstance(context)
    val jobs = manager.getWorkInfosByTag(FolderAudioDownloads.TAG).get()
    val previouslyHidden = context.dataStore.data.first()[HiddenDownloadHistory].orEmpty()
    val missingJobs = jobs.filter { work ->
        work.id.toString() !in previouslyHidden && when (work.state) {
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> true
            WorkInfo.State.SUCCEEDED -> work.outputData.getString("uri")?.let {
                documentIsMissing(context, Uri.parse(it))
            } == true
            else -> false
        }
    }
    val missingSongs = downloads.downloads.value.values.filter { download ->
        download.state == Download.STATE_COMPLETED && if (download.request.uri.scheme == "content") {
            documentIsMissing(context, download.request.uri)
        } else {
            val size = download.contentLength
            size > 0 && !downloads.downloadCache.isCached(download.request.id, 0, size)
        }
    }
    val ids = missingSongs.map { it.request.id }.toSet()
    val historyIds = missingJobs.flatMap { it.tags }.filter { it.startsWith(FolderAudioDownloads.SONG_TAG) }
        .map { it.removePrefix(FolderAudioDownloads.SONG_TAG) }.toSet()
    context.dataStore.edit { prefs ->
        val existing = prefs[HiddenDownloadHistory].orEmpty().intersect(jobs.map { it.id.toString() }.toSet())
        prefs[HiddenDownloadHistory] = existing + missingJobs.map { it.id.toString() }
        prefs[AutoLikedDownloads.SKIPPED] = prefs[AutoLikedDownloads.SKIPPED].orEmpty() + ids + historyIds
    }
    val removed = missingSongs.count { downloads.forgetMissingDownload(it) }
    removed + missingJobs.size
}
