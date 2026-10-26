/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package fr.makoff.music.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import fr.makoff.music.innertube.utils.parseCookieString
import fr.makoff.music.innertube.utils.sha1
import fr.makoff.music.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import fr.makoff.music.constants.DataSyncIdKey
import fr.makoff.music.constants.InnerTubeAuthUserKey
import fr.makoff.music.constants.InnerTubeCookieKey
import fr.makoff.music.constants.VisitorDataKey
import fr.makoff.music.db.InternalDatabase
import fr.makoff.music.db.MusicDatabase
import fr.makoff.music.db.entities.ArtistEntity
import fr.makoff.music.db.entities.Song
import fr.makoff.music.db.entities.SongEntity
import fr.makoff.music.extensions.div
import fr.makoff.music.extensions.tryOrNull
import fr.makoff.music.extensions.zipInputStream
import fr.makoff.music.extensions.zipOutputStream
import fr.makoff.music.playback.MusicService
import fr.makoff.music.playback.MusicService.Companion.PERSISTENT_AUTOMIX_FILE
import fr.makoff.music.playback.MusicService.Companion.PERSISTENT_PLAYER_STATE_FILE
import fr.makoff.music.playback.MusicService.Companion.PERSISTENT_QUEUE_FILE
import fr.makoff.music.utils.reportException
import fr.makoff.music.utils.ArtistNameAliases
import fr.makoff.music.utils.BackupArchive
import fr.makoff.music.utils.RestoreApplyActivity
import fr.makoff.music.utils.PlaylistImport
import fr.makoff.music.constants.AccountNameKey
import fr.makoff.music.constants.AccountEmailKey
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import javax.inject.Inject

data class BackupPreviewInfo(
    val hasAuthData: Boolean = false,
    val accountName: String? = null,
    val accountEmail: String? = null,
    val accountImageUrl: String? = null,
    val cookie: String? = null,
)

data class CsvImportState(
    val previewRows: List<List<String>> = emptyList(),
    val artistColumnIndex: Int = 0,
    val titleColumnIndex: Int = 1,
    val urlColumnIndex: Int = -1,
    val hasHeader: Boolean = true,
)

data class ConvertedSongLog(
    val title: String,
    val artists: String,
)

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    val database: MusicDatabase,
) : ViewModel() {
    fun backup(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { BackupArchive.write(context, database, uri) }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, if (result.isSuccess) R.string.backup_create_success
                    else R.string.backup_create_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun restore(context: Context, uri: Uri, clearAuthData: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val stage = BackupArchive.prepare(context, uri,
                    database.openHelper.writableDatabase.version, clearAuthData)
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent(context, RestoreApplyActivity::class.java)
                        .putExtra("stage", stage.name)
                        .putExtra("mainPid", android.os.Process.myPid())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun previewBackup(context: Context, uri: Uri): BackupPreviewInfo =
        runBlocking(Dispatchers.IO) {
            runCatching {
                val raw = requireNotNull(context.contentResolver.openInputStream(uri))
                raw.zipInputStream().use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.name == SETTINGS_FILENAME) {
                            val prefs = BackupArchive.readSettings(context, zip.readBytes())
                            val cookie = prefs[InnerTubeCookieKey]
                            val authenticated = cookie?.let { "SAPISID" in parseCookieString(it) } == true
                            return@runBlocking BackupPreviewInfo(
                                hasAuthData = authenticated,
                                accountName = prefs[AccountNameKey]?.takeIf(String::isNotBlank),
                                accountEmail = prefs[AccountEmailKey]?.takeIf(String::isNotBlank),
                                cookie = if (authenticated) cookie else null,
                            )
                        }
                    }
                }
                BackupPreviewInfo()
            }.getOrDefault(BackupPreviewInfo())
        }

    suspend fun fetchAccountInfoFromBackup(cookie: String): BackupPreviewInfo? {
        return runCatching {
            // Parse cookie to get SAPISID for auth header
            val cookieMap = parseCookieString(cookie)
            val sapisid = cookieMap["SAPISID"] ?: return@runCatching null

            // Generate SAPISIDHASH auth header
            val origin = "https://music.youtube.com"
            val currentTime = System.currentTimeMillis() / 1000
            val sapisidHash = sha1("$currentTime $sapisid $origin")
            val authHeader = "SAPISIDHASH ${currentTime}_$sapisidHash"

            val client = OkHttpClient()
            val requestBody = """{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"1.20240101.01.00"}}}"""
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/account/account_menu?prettyPrint=false")
                .post(requestBody)
                .header("Cookie", cookie)
                .header("Authorization", authHeader)
                .header("Origin", origin)
                .header("Referer", "$origin/")
                .header("X-Origin", origin)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@runCatching null

            // Parse the JSON response
            val json = Json { ignoreUnknownKeys = true }
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

            // Navigate to activeAccountHeaderRenderer
            val header = jsonResponse["actions"]
                ?.jsonArray?.getOrNull(0)
                ?.jsonObject?.get("openPopupAction")
                ?.jsonObject?.get("popup")
                ?.jsonObject?.get("multiPageMenuRenderer")
                ?.jsonObject?.get("header")
                ?.jsonObject?.get("activeAccountHeaderRenderer")
                ?.jsonObject

            if (header != null) {
                val name = header["accountName"]
                    ?.jsonObject?.get("runs")
                    ?.jsonArray?.getOrNull(0)
                    ?.jsonObject?.get("text")
                    ?.jsonPrimitive?.content

                val email = header["email"]
                    ?.jsonObject?.get("runs")
                    ?.jsonArray?.getOrNull(0)
                    ?.jsonObject?.get("text")
                    ?.jsonPrimitive?.content

                val thumbnailUrl = header["accountPhoto"]
                    ?.jsonObject?.get("thumbnails")
                    ?.jsonArray?.getOrNull(0)
                    ?.jsonObject?.get("url")
                    ?.jsonPrimitive?.content

                if (name != null) {
                    BackupPreviewInfo(
                        hasAuthData = true,
                        accountName = name,
                        accountEmail = email,
                        accountImageUrl = thumbnailUrl,
                        cookie = cookie,
                    )
                } else null
            } else null
        }.getOrElse {
            Timber.tag("BACKUP_PREVIEW").e(it, "Failed to fetch account info from backup")
            null
        }
    }

    fun previewCsvFile(context: Context, uri: Uri): CsvImportState = runCatching {
        val rows = requireNotNull(context.contentResolver.openInputStream(uri)).use {
            PlaylistImport.csv(it.bufferedReader().readText())
        }
        val first = rows.firstOrNull().orEmpty()
        val artist = PlaylistImport.headerIndex(first, "artist")
        val title = PlaylistImport.headerIndex(first, "title")
        val url = PlaylistImport.headerIndex(first, "url")
        CsvImportState(rows.take(6), artist.takeIf { it >= 0 } ?: 0,
            title.takeIf { it >= 0 } ?: 1, url, artist >= 0 || title >= 0 || url >= 0)
    }.getOrDefault(CsvImportState())

    suspend fun importPlaylistFromCsv(
        context: Context,
        uri: Uri,
        columnMapping: CsvImportState,
        onProgress: (Int) -> Unit = {},
        onLogUpdate: (List<ConvertedSongLog>) -> Unit = {},
    ): ArrayList<Song> = withContext(Dispatchers.IO) {
        val rows = requireNotNull(context.contentResolver.openInputStream(uri)).use {
            PlaylistImport.csv(it.bufferedReader().readText())
        }.drop(if (columnMapping.hasHeader) 1 else 0)
        val songs = arrayListOf<Song>()
        rows.forEachIndexed { index, row ->
            val id = row.getOrNull(columnMapping.urlColumnIndex)?.let(PlaylistImport::youtubeId).orEmpty()
            val title = row.getOrNull(columnMapping.titleColumnIndex).orEmpty().trim()
            val artist = row.getOrNull(columnMapping.artistColumnIndex).orEmpty().trim()
            if (title.isNotBlank() || id.isNotBlank()) {
                songs += Song(SongEntity(id = id, title = title.ifBlank { id }),
                    artists = artist.split(';').filter(String::isNotBlank).map { ArtistEntity("", it.trim()) })
                onLogUpdate(songs.takeLast(3).reversed().map {
                    ConvertedSongLog(it.title, it.artists.joinToString(", ") { artist -> artist.name })
                })
            }
            onProgress((index + 1) * 100 / rows.size.coerceAtLeast(1))
        }
        onProgress(100)
        songs
    }

    suspend fun importPlaylistFromCsv(context: Context, uri: Uri): ArrayList<Song> =
        importPlaylistFromCsv(context, uri, previewCsvFile(context, uri))

    fun loadM3UOnline(context: Context, uri: Uri): ArrayList<Song> = runCatching {
        val text = requireNotNull(context.contentResolver.openInputStream(uri)).use {
            it.bufferedReader().readText()
        }
        ArrayList(PlaylistImport.m3u(text).map { item ->
            Song(SongEntity(id = item.id, title = item.title.ifBlank { item.id }, duration = item.duration),
                artists = item.artists.map { ArtistEntity("", it) })
        })
    }.getOrDefault(arrayListOf())

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
    }
}
