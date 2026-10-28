package fr.makoff.music.downloads

import androidx.datastore.preferences.core.edit
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import fr.makoff.music.utils.dataStore
import kotlinx.coroutines.flow.first
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class FolderAudioInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val tree get() = DocumentsContract.buildTreeDocumentUri(instrumentation.context.packageName + ".foldertest", "root")

    @Before fun grantTestTreeAccess() {
        val component = instrumentation.context.packageName + "/" + GrantAudioTreeActivity::class.java.name
        val launch = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("am start -n $component"),
        ).bufferedReader().use { it.readText() }
        check(!launch.contains("Error:")) { "Cannot launch test grant activity: $launch" }
        val deadline = android.os.SystemClock.elapsedRealtime() + 10000
        while (context.checkUriPermission(tree, android.os.Process.myPid(), android.os.Process.myUid(),
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            check(android.os.SystemClock.elapsedRealtime() < deadline) { "Test document tree grant missing: $launch" }
            Thread.sleep(50)
        }
    }

    @Test(timeout = 180000) fun downloadPopupChoosesRealFilesAndSingleSongsUseAudioFolder() = runBlocking {
        val downloads = FolderAudioDownloads.downloadUtil(context)
        val preferences = context.dataStore.data.first()
        val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        val sourceFiles = mutableListOf<File>()
        val cover = File(context.cacheDir, "popup-cover-${UUID.randomUUID()}.jpg")
        val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        cover.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        sourceFiles += cover
        val documents = mutableListOf<android.net.Uri>()
        val ids = mutableListOf<String>()
        var activity: fr.makoff.music.MainActivity? = null
        fun click(id: Int) {
            val text = context.getString(id)
            var node = device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(text)), 2000)
            repeat(5) {
                if (node == null) {
                    device.swipe(device.displayWidth / 2, device.displayHeight * 7 / 10,
                        device.displayWidth / 2, device.displayHeight * 4 / 10, 35)
                    node = device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(text)), 1000)
                }
            }
            val bounds = device.findObjects(androidx.test.uiautomator.By.text(text))
                .filter { it.isEnabled && it.visibleBounds.width() > 0 }
                .maxByOrNull { it.visibleBounds.centerY() }?.visibleBounds
                ?: requireNotNull(node) { "Missing download popup control: $text" }.visibleBounds
            assertTrue(device.click(bounds.centerX(), bounds.centerY()))
            device.waitForIdle()
        }
        try {
            context.dataStore.edit {
                it[fr.makoff.music.constants.AutoDownloadOnLikeKey] = false
                it[fr.makoff.music.constants.FolderAudioTreeKey] = tree.toString()
                it[fr.makoff.music.constants.UnifiedDownloadFormatKey] = DownloadAudioFormat.BEST.name
            }
            context.startActivity(android.content.Intent(context, fr.makoff.music.MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            val activityDeadline = android.os.SystemClock.elapsedRealtime() + 15000
            while (activity == null && android.os.SystemClock.elapsedRealtime() < activityDeadline) {
                instrumentation.runOnMainSync {
                    activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                        .filterIsInstance<fr.makoff.music.MainActivity>().firstOrNull()
                }
                kotlinx.coroutines.delay(100)
            }
            assertNotNull("Production activity opens", activity)
            for ((index, format) in (DownloadAudioFormat.entries + listOf(DownloadAudioFormat.M4A, DownloadAudioFormat.M4A)).withIndex()) {
                val id = UUID.randomUUID().toString().replace("-", "").take(11)
                ids += id
                val albumTitle = "Album popup $id"
                val media = fr.makoff.music.models.MediaMetadata(id, "Popup format $id", emptyList(), 1,
                    album = fr.makoff.music.models.MediaMetadata.Album("album-$id", albumTitle),
                    thumbnailUrl = android.net.Uri.fromFile(cover).toString())
                val asset = if (format == DownloadAudioFormat.BEST) "opus.webm" else "aac.m4a"
                val source = File(context.cacheDir, "popup-$id.$asset").apply {
                    writeBytes(android.util.Base64.decode(instrumentation.context.assets.open("audio/$asset.base64").use { it.readBytes() }, android.util.Base64.DEFAULT))
                }
                sourceFiles += source
                val original = saveAudioToTree(context, tree, source, "popup-source-$id.${source.extension}")
                documents += original
                downloads.registerExternalAudio(media, original, source.length())
                val collection = index >= 3
                instrumentation.runOnMainSync {
                    activity!!.setContent {
                        androidx.compose.material3.MaterialTheme {
                            val item = if (collection) rememberFolderCollectionItem(if (index == 4) "Sélections" else "Playlists", "Popup playlist $id", id) { listOf(media) }
                                else rememberFolderAudioItem(media)
                            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(32.dp)) {
                                androidx.compose.material3.TextButton(onClick = { item.onClick?.invoke() }) { item.title() }
                            }
                        }
                    }
                }
                click(fr.makoff.music.R.string.action_download)
                assertTrue("Quality popup appears before work starts", device.wait(androidx.test.uiautomator.Until.hasObject(
                    androidx.test.uiautomator.By.text(context.getString(fr.makoff.music.R.string.download_choose_quality))), 5000))
                assertTrue("Opening the popup must not queue a download", androidx.work.WorkManager.getInstance(context)
                    .getWorkInfosByTag(FolderAudioDownloads.SONG_TAG + id).get().isEmpty())
                if (index == 0) {
                    device.executeShellCommand("screencap -p /data/local/tmp/download-quality-popup.png")
                    click(android.R.string.cancel)
                    assertTrue("Cancel must not queue a download", androidx.work.WorkManager.getInstance(context)
                        .getWorkInfosByTag(FolderAudioDownloads.SONG_TAG + id).get().isEmpty())
                    click(fr.makoff.music.R.string.action_download)
                }
                click(format.label)
                click(fr.makoff.music.R.string.action_download)
                // Change the default immediately: the confirmed request must retain its own choice.
                context.dataStore.edit { it[fr.makoff.music.constants.UnifiedDownloadFormatKey] =
                    if (format == DownloadAudioFormat.FLAC) DownloadAudioFormat.M4A.name else DownloadAudioFormat.FLAC.name }
                var completed: androidx.work.WorkInfo? = null
                val deadline = android.os.SystemClock.elapsedRealtime() + 45000
                while (completed == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                    completed = androidx.work.WorkManager.getInstance(context).getWorkInfosByTag(FolderAudioDownloads.SONG_TAG + id)
                        .get().firstOrNull { it.state.isFinished }
                    kotlinx.coroutines.delay(100)
                }
                assertEquals("Confirmed format exports successfully", androidx.work.WorkInfo.State.SUCCEEDED, completed?.state)
                val uri = android.net.Uri.parse(requireNotNull(completed!!.outputData.getString("uri")))
                documents += uri
                val root = requireNotNull(DocumentFile.fromTreeUri(context, tree))
                val folder = if (index == 3) root.findFile("Playlists")?.findFile("Popup playlist $id") else root.findFile("Audio")
                assertTrue("File belongs to the action's folder", requireNotNull(folder).listFiles().any { it.uri == uri })
                assertNull("Single-song album metadata must not create an album directory", root.findFile("Albums")?.findFile(albumTitle))
                if (index != 3) assertFalse("Individual audio must not have a collection track prefix",
                    completed.outputData.getString("name").orEmpty().startsWith("001 - "))
                val expectedMime = when (format) {
                    DownloadAudioFormat.BEST -> "audio/ogg"
                    DownloadAudioFormat.M4A -> "audio/mp4"
                    DownloadAudioFormat.FLAC -> "audio/flac"
                }
                assertEquals(expectedMime, completed.outputData.getString("mime"))
                val extractor = android.media.MediaExtractor()
                try {
                    extractor.setDataSource(context, uri, null)
                    val track = extractor.getTrackFormat(0)
                    val actual = context.contentResolver.openInputStream(uri)!!.use {
                        sourceAudioMime(it, requireNotNull(track.getString(android.media.MediaFormat.KEY_MIME)))
                    }
                    assertEquals(when (format) {
                        DownloadAudioFormat.BEST -> "audio/opus"
                        DownloadAudioFormat.M4A -> "audio/mp4a-latm"
                        DownloadAudioFormat.FLAC -> "audio/flac"
                    }, actual)
                } finally { extractor.release() }
                assertEquals("Export also remains the actual offline source", uri, downloads.externalAudioUri(id))
                println("DOWNLOAD_POPUP_FORMAT_OK: ${format.name}; real codec, fixed per-request selection, ${if (index == 3) "playlist" else "Audio"} folder and shared offline source")
            }
        } finally {
            ids.forEach { id ->
                androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag(FolderAudioDownloads.SONG_TAG + id).result.get()
                (downloads.downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).removeDownload(id)
                downloads.downloads.value = downloads.downloads.value - id
                downloads.database.songEntity(id)?.let { downloads.database.delete(it) }
            }
            documents.forEach { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
            sourceFiles.forEach { it.delete() }
            context.dataStore.updateData { preferences }
            instrumentation.runOnMainSync { activity?.finish() }
        }
    }

    @Test(timeout = 90000) fun exportedAudioContainsPortableTagsAndEmbeddedCover() = runBlocking {
        val root = File(context.cacheDir, "metadata-${UUID.randomUUID()}").apply { mkdirs() }
        val cover = File(root, "cover.jpg")
        val bitmap = android.graphics.Bitmap.createBitmap(1024, 576, android.graphics.Bitmap.Config.ARGB_8888)
        val random = java.util.Random(42)
        bitmap.setPixels(IntArray(1024 * 576) { index ->
            when {
                index % 1024 < 96 -> android.graphics.Color.RED
                index % 1024 >= 928 -> android.graphics.Color.BLUE
                else -> android.graphics.Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            }
        }, 0, 1024, 0, 0, 1024, 576)
        cover.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        val title = "Title with 'quotes' and % signs"
        val media = fr.makoff.music.models.MediaMetadata("BaW_jenozKc", title,
            listOf(fr.makoff.music.models.MediaMetadata.Artist("test", "Audio artist")), 1,
            thumbnailUrl = android.net.Uri.fromFile(cover).toString(),
            album = fr.makoff.music.models.MediaMetadata.Album("album-test", "Audio album"))
        try {
            for (format in DownloadAudioFormat.entries) {
                val dir = File(root, format.name).apply { mkdirs() }
                val asset = if (format == DownloadAudioFormat.BEST) "opus.webm" else "aac.m4a"
                val source = File(dir, asset).apply { writeBytes(android.util.Base64.decode(
                    instrumentation.context.assets.open("audio/$asset.base64").use { it.readBytes() }, android.util.Base64.DEFAULT)) }
                val raw = YtDlpAudioEngine.reuse(context, source, format, dir, "metadata-${format.name}")!!
                val tagged = tagAudioFile(context, raw, media, 2, dir)
                assertArrayEquals("Tagging must preserve encoded audio", audioPackets(raw), audioPackets(tagged))
                val uri = saveAudioToTree(context, tree, tagged, "metadata-${UUID.randomUUID()}.${tagged.extension}")
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        assertEquals(title, retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE))
                        assertEquals("Audio artist", retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST))
                        assertEquals("Audio album", retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM))
                        assertEquals("2", retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/'))
                        val picture = requireNotNull(retriever.embeddedPicture) { "Embedded cover for ${tagged.extension}" }
                        assertTrue("Detailed artwork must not be degraded to the old 64 KB limit", picture.size > 64000)
                        assertTrue("Embedded artwork remains bounded", picture.size <= 2 * 1024 * 1024)
                        val decoded = android.graphics.BitmapFactory.decodeByteArray(picture, 0, picture.size)
                        assertNotNull("Complete, decodable cover for ${tagged.extension}", decoded)
                        requireNotNull(decoded)
                        assertEquals("Detailed cover retains full source resolution", 1024, decoded.width)
                        assertEquals("Square canvas avoids viewer-side cropping", decoded.width, decoded.height)
                        assertTrue("Left edge retained", android.graphics.Color.red(decoded.getPixel(3, decoded.height / 2)) > 180)
                        assertTrue("Right edge retained", android.graphics.Color.blue(decoded.getPixel(decoded.width - 4, decoded.height / 2)) > 180)
                        assertTrue("Letterbox retains the complete wide picture", android.graphics.Color.red(decoded.getPixel(decoded.width / 2, 3)) < 30)
                        decoded.recycle()
                    } finally { retriever.release() }
                } finally { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                println("AUDIO_TAGS_OK: ${tagged.extension}; title, artist, album, full-resolution 1024px artwork above 64 KB, both edges, letterbox and unchanged audio")
            }
        } finally { root.deleteRecursively() }
    }

    @Test(timeout = 60000) fun clearDownloadsRemovesNativeAndSharedEntriesAndHonorsPublicFileChoice() = runBlocking {
        val downloads = FolderAudioDownloads.downloadUtil(context)
        val originalPreferences = context.dataStore.data.first()
        val sharedId = UUID.randomUUID().toString().replace("-", "").take(11)
        val nativeId = UUID.randomUUID().toString().replace("-", "").take(11)
        val shared = fr.makoff.music.models.MediaMetadata(sharedId, "Clear shared audio", emptyList(), 1)
        val native = fr.makoff.music.models.MediaMetadata(nativeId, "Clear private audio", emptyList(), 1)
        val source = File(context.cacheDir, "clear-$sharedId.m4a").apply { writeBytes(android.util.Base64.decode(
            instrumentation.context.assets.open("audio/aac.m4a.base64").use { it.readBytes() }, android.util.Base64.DEFAULT)) }
        var uri: android.net.Uri? = null
        try {
            context.dataStore.edit { it[fr.makoff.music.constants.AutoDownloadOnLikeKey] = false }
            uri = saveAudioToTree(context, tree, source, "clear-$sharedId.m4a")
            downloads.registerExternalAudio(shared, uri!!, source.length())
            downloads.database.update(requireNotNull(downloads.database.songEntity(sharedId)).toggleLike(syncToYouTube = false))
            downloads.database.insert(native)
            val cache = downloads.downloadCache
            val hole = cache.startReadWrite(nativeId, 0, source.length())
            try {
                val file = cache.startFile(nativeId, 0, source.length())
                file.writeBytes(source.readBytes())
                cache.commitFile(file, source.length())
            } finally { cache.releaseHoleSpan(hole) }
            val request = androidx.media3.exoplayer.offline.DownloadRequest.Builder(nativeId, android.net.Uri.parse(nativeId))
                .setCustomCacheKey(nativeId).build()
            val completed = androidx.media3.exoplayer.offline.Download(request,
                androidx.media3.exoplayer.offline.Download.STATE_COMPLETED, 1, 1, source.length(), 0, 0)
            (downloads.downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).putDownload(completed)
            downloads.downloads.value = downloads.downloads.value + (nativeId to completed)
            downloads.database.updateDownloadedInfo(nativeId, true, java.time.LocalDateTime.now())
            assertEquals(0, downloads.clearDownloads(deleteExportedFiles = false))
            assertTrue("Queue and shared-document index cleared", downloads.downloads.value.isEmpty())
            assertNull(downloads.downloadManager.downloadIndex.getDownload(nativeId))
            assertNull(downloads.downloadManager.downloadIndex.getDownload(sharedId))
            assertEquals("Private audio bytes cleared", 0L, cache.getCachedBytes(nativeId, 0, source.length()))
            assertFalse(requireNotNull(downloads.database.songEntity(nativeId)).isDownloaded)
            assertFalse(requireNotNull(downloads.database.songEntity(sharedId)).isDownloaded)
            assertTrue("Public file kept when unchecked", context.contentResolver.openAssetFileDescriptor(uri!!, "r")!!.use { it.length > 0 })
            assertTrue("Explicit removal persisted for auto-like", sharedId in context.dataStore.data.first()[AutoLikedDownloads.SKIPPED].orEmpty())
            context.dataStore.edit { it[fr.makoff.music.constants.AutoDownloadOnLikeKey] = true }
            kotlinx.coroutines.delay(1200)
            assertNull("Auto-like must not undo clear", downloads.externalAudioUri(sharedId))
            context.dataStore.edit { it[fr.makoff.music.constants.AutoDownloadOnLikeKey] = false }
            downloads.registerExternalAudio(shared, uri!!, source.length())
            assertEquals(0, downloads.clearDownloads(deleteExportedFiles = true))
            assertFalse("Opt-in public file deletion", requireNotNull(DocumentFile.fromSingleUri(context, uri!!)).exists())
            assertTrue("Deleted audio must actually be unavailable for reading", runCatching {
                context.contentResolver.openAssetFileDescriptor(uri!!, "r")?.use { it.length }
            }.isFailure)
            assertFalse(requireNotNull(downloads.database.songEntity(sharedId)).isDownloaded)
            assertTrue("Library and likes retained", requireNotNull(downloads.database.songEntity(sharedId)).liked)
            println("CLEAR_DOWNLOADS_OK: native bytes and index removed, shared reference removed, public-file choice honored, library and likes kept, auto-like cannot restore cleared audio")
        } finally {
            uri?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
            for (id in listOf(sharedId, nativeId)) {
                (downloads.downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).removeDownload(id)
                downloads.downloads.value = downloads.downloads.value - id
                downloads.downloadCache.removeResource(id)
                downloads.database.songEntity(id)?.let { downloads.database.delete(it) }
            }
            source.delete()
            context.dataStore.updateData { originalPreferences }
        }
    }

    @Test(timeout = 60000) fun cleanupHandlesAndroidProviderErrorsAndFailedHistory() = runBlocking {
        val downloads = FolderAudioDownloads.downloadUtil(context)
        val before = context.dataStore.data.first()
        val manager = androidx.work.WorkManager.getInstance(context)
        val requests = mutableListOf<java.util.UUID>()
        try {
            val root = requireNotNull(DocumentFile.fromTreeUri(context, tree))
            for (prefix in listOf("query-null", "query-error", "query-denied")) {
                val document = requireNotNull(root.createFile("audio/mp4", "$prefix-${UUID.randomUUID()}.m4a"))
                if (prefix != "query-denied") {
                    DocumentsContract.deleteDocument(context.contentResolver, document.uri)
                    assertTrue("Deleted file with $prefix must be recognized", documentIsMissing(context, document.uri))
                } else {
                    assertFalse("Permission errors must not remove entries", documentIsMissing(context, document.uri))
                    DocumentsContract.deleteDocument(context.contentResolver, document.uri)
                }
            }
            val failed = androidx.work.OneTimeWorkRequestBuilder<FolderAudioWorker>()
                .setInputData(androidx.work.workDataOf("tree" to tree.toString(), "videoId" to "invalid"))
                .addTag(FolderAudioDownloads.TAG).build()
            val cancelled = androidx.work.OneTimeWorkRequestBuilder<FolderAudioWorker>()
                .setInitialDelay(1, java.util.concurrent.TimeUnit.DAYS).addTag(FolderAudioDownloads.TAG).build()
            val active = androidx.work.OneTimeWorkRequestBuilder<FolderAudioWorker>()
                .setInitialDelay(1, java.util.concurrent.TimeUnit.DAYS).addTag(FolderAudioDownloads.TAG).build()
            requests += listOf(failed.id, cancelled.id, active.id)
            manager.enqueue(listOf(failed, cancelled, active)).result.get()
            manager.cancelWorkById(cancelled.id).result.get()
            val deadline = android.os.SystemClock.elapsedRealtime() + 20000
            while (manager.getWorkInfoById(failed.id).get()?.state != androidx.work.WorkInfo.State.FAILED) {
                check(android.os.SystemClock.elapsedRealtime() < deadline)
                kotlinx.coroutines.delay(50)
            }
            cleanMissingDownloads(context, downloads)
            val hidden = context.dataStore.data.first()[HiddenDownloadHistory].orEmpty()
            assertTrue(failed.id.toString() in hidden)
            assertTrue(cancelled.id.toString() in hidden)
            assertFalse(active.id.toString() in hidden)
            println("ANDROID_PROVIDER_CLEANUP_OK: null cursors and provider exceptions handled; denied documents and active downloads preserved; failed/cancelled history removed")
        } finally {
            requests.forEach { manager.cancelWorkById(it).result.get() }
            context.dataStore.updateData { before }
        }
    }

    @Test(timeout = 60000) fun cacheLimitsApplyImmediatelyAndKeepOfflineAudio() = runBlocking {
        val downloads = FolderAudioDownloads.downloadUtil(context)
        val before = context.dataStore.data.first()
        val privateKeys = downloads.downloadCache.keys.toSet()
        val directory = File(context.cacheDir, "cache-limit-${UUID.randomUUID()}")
        val evictor = fr.makoff.music.playback.ResizableCacheEvictor(4096)
        val cache = androidx.media3.datasource.cache.SimpleCache(directory, evictor, downloads.databaseProvider)
        fun write(target: androidx.media3.datasource.cache.Cache, key: String) {
            val hole = target.startReadWrite(key, 0, 1024)
            try {
                val file = target.startFile(key, 0, 1024)
                file.writeBytes(ByteArray(1024) { 1 })
                target.commitFile(file, 1024)
            } finally { target.releaseHoleSpan(hole) }
        }
        val key = "resize-${UUID.randomUUID()}"
        try {
            write(cache, "old"); Thread.sleep(5); write(cache, "recent")
            evictor.resize(cache, 1024)
            assertTrue(cache.getCachedSpans("old").isEmpty())
            assertTrue(cache.isCached("recent", 0, 1024))
            evictor.resize(cache, 0)
            assertEquals(0L, cache.cacheSpace)
            evictor.resize(cache, Long.MAX_VALUE)
            write(cache, "unlimited")
            assertEquals(1024L, cache.cacheSpace)
            context.dataStore.edit { it[fr.makoff.music.constants.MaxSongCacheSizeKey] = 128 }
            kotlinx.coroutines.delay(300)
            write(downloads.playerCache, key)
            assertTrue(downloads.playerCache.isCached(key, 0, 1024))
            context.dataStore.edit { it[fr.makoff.music.constants.MaxSongCacheSizeKey] = 0 }
            val deadline = android.os.SystemClock.elapsedRealtime() + 10000
            while (downloads.playerCache.getCachedSpans(key).isNotEmpty()) {
                check(android.os.SystemClock.elapsedRealtime() < deadline) { "Production streaming cache did not resize" }
                kotlinx.coroutines.delay(50)
            }
            assertEquals(privateKeys, downloads.downloadCache.keys)
            println("LIVE_CACHE_LIMIT_OK: real LRU trimming, disabled/unlimited modes and live production preference; offline cache untouched")
        } finally {
            cache.release(); directory.deleteRecursively()
            downloads.playerCache.removeResource(key)
            context.dataStore.updateData { before }
        }
    }

    @Test(timeout = 90000) fun missingDownloadCleanupKeepsReadableFilesAndLibrary() = runBlocking {
        val downloads = FolderAudioDownloads.downloadUtil(context)
        val before = context.dataStore.data.first()
        val tracks = (0..3).map { index -> fr.makoff.music.models.MediaMetadata(
            UUID.randomUUID().toString().replace("-", "").take(11), "Cleanup test $index", emptyList(), 1) }
        val source = File(context.cacheDir, "missing-cleanup.m4a").apply { writeBytes(android.util.Base64.decode(
            instrumentation.context.assets.open("audio/aac.m4a.base64").use { it.readBytes() }, android.util.Base64.DEFAULT)) }
        val documents = mutableListOf<android.net.Uri>()
        try {
            context.dataStore.edit { it[fr.makoff.music.constants.AutoDownloadOnLikeKey] = false }
            for (track in tracks.take(2)) {
                val uri = saveAudioToTree(context, tree, source, "cleanup-${track.id}.m4a")
                documents += uri
                downloads.registerExternalAudio(track, uri, source.length())
            }
            val missing = tracks[0]
            downloads.database.update(requireNotNull(downloads.database.songEntity(missing.id)).toggleLike(syncToYouTube = false))
            FolderAudioDownloads.enqueue(context, missing, tree, listOf("Cleanup"))
            var work: androidx.work.WorkInfo? = null
            val deadline = android.os.SystemClock.elapsedRealtime() + 45000
            while (work == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                work = androidx.work.WorkManager.getInstance(context).getWorkInfosByTag(FolderAudioDownloads.SONG_TAG + missing.id)
                    .get().firstOrNull { it.state.isFinished }
                kotlinx.coroutines.delay(100)
            }
            assertEquals(androidx.work.WorkInfo.State.SUCCEEDED, work?.state)
            val exported = android.net.Uri.parse(requireNotNull(work!!.outputData.getString("uri")))
            documents += exported
            assertFalse(documentIsMissing(context, exported))
            DocumentsContract.deleteDocument(context.contentResolver, exported)
            assertTrue(documentIsMissing(context, exported))
            assertFalse("Unavailable provider must be preserved", documentIsMissing(context,
                android.net.Uri.parse("content://missing.invalid/tree/root/document/deleted")))
            for ((index, track) in tracks.withIndex().filter { it.index >= 2 }) {
                downloads.database.insert(track)
                if (index == 3) {
                    val hole = downloads.downloadCache.startReadWrite(track.id, 0, source.length())
                    try {
                        val file = downloads.downloadCache.startFile(track.id, 0, source.length())
                        file.writeBytes(source.readBytes())
                        downloads.downloadCache.commitFile(file, source.length())
                    } finally { downloads.downloadCache.releaseHoleSpan(hole) }
                }
                val request = androidx.media3.exoplayer.offline.DownloadRequest.Builder(track.id, android.net.Uri.parse(track.id))
                    .setCustomCacheKey(track.id).build()
                val completed = androidx.media3.exoplayer.offline.Download(request,
                    androidx.media3.exoplayer.offline.Download.STATE_COMPLETED, 1, 1, source.length(), 0, 0)
                (downloads.downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).putDownload(completed)
                downloads.downloads.value = downloads.downloads.value + (track.id to completed)
                downloads.database.updateDownloadedInfo(track.id, true, java.time.LocalDateTime.now())
            }
            assertTrue(cleanMissingDownloads(context, downloads) >= 2)
            assertNull(downloads.downloads.value[missing.id])
            assertNull(downloads.downloadManager.downloadIndex.getDownload(tracks[2].id))
            assertFalse(requireNotNull(downloads.database.songEntity(missing.id)).isDownloaded)
            assertTrue("Like and library metadata retained", requireNotNull(downloads.database.songEntity(missing.id)).liked)
            assertTrue(missing.id in context.dataStore.data.first()[AutoLikedDownloads.SKIPPED].orEmpty())
            assertTrue(work.id.toString() in context.dataStore.data.first()[HiddenDownloadHistory].orEmpty())
            assertEquals(documents[1], downloads.externalAudioUri(tracks[1].id))
            assertTrue(downloads.downloadCache.isCached(tracks[3].id, 0, source.length()))
            assertNotNull(downloads.downloads.value[tracks[3].id])
            println("MISSING_DOWNLOAD_CLEANUP_OK: vanished documents/history and absent private bytes removed; readable public/private audio, likes and metadata kept; unavailable provider preserved")
        } finally {
            documents.forEach { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
            tracks.forEach { track ->
                (downloads.downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).removeDownload(track.id)
                downloads.downloads.value = downloads.downloads.value - track.id
                downloads.downloadCache.removeResource(track.id)
                downloads.database.songEntity(track.id)?.let { downloads.database.delete(it) }
            }
            source.delete()
            context.dataStore.updateData { before }
        }
    }

    @Test(timeout = 90000) fun manualLikedDownloadWithAlbumExportsIntoLikedAndKeepsHistoryArtwork() = runBlocking {
        val downloads = FolderAudioDownloads.downloadUtil(context)
        val originalPreferences = context.dataStore.data.first()
        val id = UUID.randomUUID().toString().replace("-", "").take(11)
        val cover = File(context.filesDir, "history-liked-cover-$id.jpg")
        val image = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.BLUE)
        cover.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
        image.recycle()
        val media = fr.makoff.music.models.MediaMetadata(id, "Liked album routing test", emptyList(), 1, explicit = true,
            thumbnailUrl = android.net.Uri.fromFile(cover).toString(),
            album = fr.makoff.music.models.MediaMetadata.Album("album-$id", "Album $id"))
        val source = File(context.cacheDir, "manual-liked-$id.m4a").apply { writeBytes(android.util.Base64.decode(
            instrumentation.context.assets.open("audio/aac.m4a.base64").use { it.readBytes() }, android.util.Base64.DEFAULT)) }
        var original: android.net.Uri? = null
        var exported: android.net.Uri? = null
        var succeeded = false
        try {
            context.dataStore.edit {
                it[fr.makoff.music.constants.AutoDownloadOnLikeKey] = false
                it[fr.makoff.music.constants.UnifiedDownloadFormatKey] = DownloadAudioFormat.BEST.name
            }
            original = saveAudioToTree(context, tree, source, "manual-source-$id.m4a")
            downloads.registerExternalAudio(media, original!!, source.length())
            downloads.database.update(requireNotNull(downloads.database.songEntity(id)).toggleLike(syncToYouTube = false))
            // Manual single-song action intentionally supplies no collection folder.
            FolderAudioDownloads.enqueue(context, media, tree)
            var completed: androidx.work.WorkInfo? = null
            val deadline = android.os.SystemClock.elapsedRealtime() + 45000
            while (completed == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                completed = androidx.work.WorkManager.getInstance(context)
                    .getWorkInfosByTag(FolderAudioDownloads.SONG_TAG + id).get().firstOrNull { it.state.isFinished }
                kotlinx.coroutines.delay(100)
            }
            assertNotNull("Manual export completes", completed)
            assertEquals(androidx.work.WorkInfo.State.SUCCEEDED, completed!!.state)
            exported = android.net.Uri.parse(requireNotNull(completed.outputData.getString("uri")))
            val likedFolder = requireNotNull(DocumentFile.fromTreeUri(context, tree)?.findFile(AutoLikedDownloads.FOLDER))
            assertTrue("Manual liked song belongs to J'aime", likedFolder.listFiles().any { it.uri == exported })
            assertNull("Album metadata must not create an album export", DocumentFile.fromTreeUri(context, tree)
                ?.findFile("Albums")?.findFile("Album $id"))
            assertEquals(exported, downloads.externalAudioUri(id))
            assertEquals(android.net.Uri.fromFile(cover).toString(), completed.outputData.getString("thumbnail"))
            assertEquals(exported.toString(), context.dataStore.data.first()[AutoLikedDownloads.documentKey(tree.toString(), id)])
            assertTrue(requireNotNull(downloads.database.songEntity(id)).explicit)
            println("EXPLICIT_OFFLINE_EXPORT_OK: explicitly marked audio exports and remains available offline")
            succeeded = true
            println("MANUAL_LIKED_FOLDER_OK: manual download with album metadata exports to J'aime, keeps offline reference and history thumbnail")
        } finally {
            androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag(FolderAudioDownloads.SONG_TAG + id).result.get()
            (downloads.downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).removeDownload(id)
            downloads.downloads.value = downloads.downloads.value - id
            downloads.database.songEntity(id)?.let { downloads.database.delete(it) }
            listOfNotNull(original, exported).forEach { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
            source.delete()
            if (!succeeded) cover.delete()
            context.dataStore.updateData { originalPreferences }
        }
    }

    @Test fun exportWaitsForNativeCompletionAndStopsOnFailureOrCancellation() = runBlocking {
        val request = androidx.media3.exoplayer.offline.DownloadRequest.Builder("test-native", android.net.Uri.parse("https://example.invalid/audio")).build()
        fun download(state: Int) = androidx.media3.exoplayer.offline.Download(request, state, 1, 1, 100, 0,
            if (state == androidx.media3.exoplayer.offline.Download.STATE_FAILED) androidx.media3.exoplayer.offline.Download.FAILURE_REASON_UNKNOWN else 0)
        val states = listOf(androidx.media3.exoplayer.offline.Download.STATE_QUEUED,
            androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING,
            androidx.media3.exoplayer.offline.Download.STATE_COMPLETED).iterator()
        var reads = 0
        awaitOfflineAudio { reads++; download(states.next()) }
        assertEquals(3, reads)
        for (state in listOf(androidx.media3.exoplayer.offline.Download.STATE_FAILED,
            androidx.media3.exoplayer.offline.Download.STATE_STOPPED,
            androidx.media3.exoplayer.offline.Download.STATE_REMOVING)) {
            assertTrue(runCatching { awaitOfflineAudio { download(state) } }.isFailure)
        }
        var removed = false
        assertTrue(runCatching { awaitOfflineAudio {
            if (removed) null else download(androidx.media3.exoplayer.offline.Download.STATE_QUEUED).also { removed = true }
        } }.isFailure)
        println("NATIVE_EXPORT_QUEUE_OK: waits for native completion, stops on failed/stopped/removed downloads without another transfer")
    }

    @Test(timeout = 90000) fun likingAutomaticallyExportsToLikedFolderAndKeepsOfflineReference() = runBlocking {
        val downloads = FolderAudioDownloads.downloadUtil(context)
        val id = UUID.randomUUID().toString().replace("-", "").take(11)
        val media = fr.makoff.music.models.MediaMetadata(id, "Liked audio test", emptyList(), 1,
            album = fr.makoff.music.models.MediaMetadata.Album("auto-album-$id", "Album $id"))
        val prefs = context.dataStore.data.first()
        val enabled = fr.makoff.music.constants.AutoDownloadOnLikeKey
        val destination = fr.makoff.music.constants.FolderAudioTreeKey
        val format = fr.makoff.music.constants.ExternalDownloadFormatKey
        val documentKey = AutoLikedDownloads.documentKey(tree.toString(), id)
        val bytes = android.util.Base64.decode(instrumentation.context.assets.open("audio/aac.m4a.base64").use { it.readBytes() }, android.util.Base64.DEFAULT)
        val source = File(context.cacheDir, "liked-$id.m4a").apply { writeBytes(bytes) }
        var original: android.net.Uri? = null
        var exported: android.net.Uri? = null
        try {
            context.dataStore.edit { it[enabled] = false; it[destination] = tree.toString(); it[format] = DownloadAudioFormat.BEST.name }
            original = saveAudioToTree(context, tree, source, "source-$id.m4a")
            downloads.registerExternalAudio(media, original!!, bytes.size.toLong())
            context.dataStore.edit { it[enabled] = true }
            downloads.database.update(requireNotNull(downloads.database.songEntity(id)).toggleLike(syncToYouTube = false))
            val deadline = android.os.SystemClock.elapsedRealtime() + 45000
            while (exported == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                exported = context.dataStore.data.first()[documentKey]?.let(android.net.Uri::parse)
                kotlinx.coroutines.delay(100)
            }
            assertNotNull("A like must automatically create a public audio file", exported)
            assertEquals(exported, downloads.externalAudioUri(id))
            assertTrue(requireNotNull(downloads.database.songEntity(id)).isDownloaded)
            val taggedLiked = File(context.cacheDir, "tagged-liked-$id.m4a")
            try {
                taggedLiked.writeBytes(context.contentResolver.openInputStream(exported!!)!!.use { it.readBytes() })
                assertArrayEquals("Automatic export must keep the same encoded audio", audioPackets(source), audioPackets(taggedLiked))
                val tags = android.media.MediaMetadataRetriever()
                try {
                    tags.setDataSource(taggedLiked.absolutePath)
                    assertEquals(media.title, tags.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE))
                } finally { tags.release() }
            } finally { taggedLiked.delete() }
            val likedFolder = requireNotNull(DocumentFile.fromTreeUri(context, tree)?.findFile(AutoLikedDownloads.FOLDER))
            assertTrue(likedFolder.listFiles().any { it.uri == exported })
            val count = likedFolder.listFiles().size
            downloads.database.update(requireNotNull(downloads.database.songEntity(id)).toggleLike(syncToYouTube = false))
            kotlinx.coroutines.delay(500)
            downloads.database.update(requireNotNull(downloads.database.songEntity(id)).toggleLike(syncToYouTube = false))
            kotlinx.coroutines.delay(1000)
            assertEquals("Reliking must reuse the saved file", count, likedFolder.listFiles().size)
            assertEquals(exported.toString(), context.dataStore.data.first()[documentKey])
            println("AUTO_LIKED_OK: automatic export into J'aime, exact audio bytes and offline reference; relike reuses file")
        } finally {
            context.dataStore.edit {
                it[enabled] = false
                it.remove(documentKey)
            }
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork(
                "folder-audio-$id-${destinationId(tree.toString() + AutoLikedDownloads.FOLDER + 0)}"
            ).result.get()
            val notifications = context.getSystemService(android.app.NotificationManager::class.java)
            notifications.activeNotifications.filter {
                it.notification.extras.getString(android.app.Notification.EXTRA_TITLE) == media.title
            }.forEach { notifications.cancel(it.tag, it.id) }
            (downloads.downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).removeDownload(id)
            downloads.downloads.value = downloads.downloads.value - id
            downloads.database.songEntity(id)?.let { downloads.database.delete(it) }
            listOfNotNull(original, exported).forEach { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } }
            source.delete()
            context.dataStore.edit {
                prefs[enabled]?.let { value -> it[enabled] = value } ?: it.remove(enabled)
                prefs[destination]?.let { value -> it[destination] = value } ?: it.remove(destination)
                prefs[format]?.let { value -> it[format] = value } ?: it.remove(format)
            }
        }
    }

    @Test fun sharedDocumentIsOfflineDownloadAndMissingDocumentCanBeRetried() = runBlocking {
        val downloads = FolderAudioDownloads.downloadUtil(context)
        val id = UUID.randomUUID().toString().replace("-", "").take(11)
        val media = fr.makoff.music.models.MediaMetadata(id, "Shared audio test", emptyList(), 1)
        val bytes = android.util.Base64.decode(instrumentation.context.assets.open("audio/aac.m4a.base64").use { it.readBytes() }, android.util.Base64.DEFAULT)
        val directory = File(context.cacheDir, "shared-test-$id").apply { mkdirs() }
        val source = File(directory, "source.m4a").apply { writeBytes(bytes) }
        val uri = saveAudioToTree(context, tree, source, "$id.m4a")
        try {
            downloads.registerExternalAudio(media, uri, bytes.size.toLong())
            assertEquals(uri, downloads.externalAudioUri(id))
            assertEquals(uri, androidx.media3.exoplayer.offline.DefaultDownloadIndex(downloads.databaseProvider).getDownload(id)?.request?.uri)
            assertEquals(androidx.media3.exoplayer.offline.Download.STATE_COMPLETED, downloads.downloads.value[id]?.state)
            assertTrue(downloads.database.getSongByIdBlocking(id)!!.song.isDownloaded)
            assertTrue(downloads.downloadCache.getCachedSpans(id).isEmpty())
            val staged = downloads.stageExistingAudio(id, File(directory, "staging"))!!
            assertArrayEquals(bytes, staged.readBytes())
            assertEquals("m4a", staged.extension)
            assertTrue(DocumentsContract.deleteDocument(context.contentResolver, uri))
            assertNull(downloads.externalAudioUri(id))
            assertNull(downloads.downloadManager.downloadIndex.getDownload(id))
            assertFalse(downloads.database.getSongByIdBlocking(id)!!.song.isDownloaded)
        } finally {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            downloads.database.getSongByIdBlocking(id)?.let { downloads.database.delete(it.song) }
            directory.deleteRecursively()
        }
    }

    @Test fun removingSharedDownloadKeepsPersonalFile() = runBlocking {
        val downloads = FolderAudioDownloads.downloadUtil(context)
        val id = UUID.randomUUID().toString().replace("-", "").take(11)
        val bytes = android.util.Base64.decode(instrumentation.context.assets.open("audio/aac.m4a.base64").use { it.readBytes() }, android.util.Base64.DEFAULT)
        val source = File(context.cacheDir, "$id.m4a").apply { writeBytes(bytes) }
        val uri = saveAudioToTree(context, tree, source, "$id.m4a")
        try {
            downloads.registerExternalAudio(fr.makoff.music.models.MediaMetadata(id, "Removal test", emptyList(), 1), uri, bytes.size.toLong())
            instrumentation.runOnMainSync { downloads.downloadManager.removeDownload(id) }
            val deadline = android.os.SystemClock.elapsedRealtime() + 15000
            while (downloads.downloads.value.containsKey(id)) {
                check(android.os.SystemClock.elapsedRealtime() < deadline) { "Removal did not finish: memory=${downloads.downloads.value[id]?.state}; index=${downloads.downloadManager.downloadIndex.getDownload(id)?.state}" }
                kotlinx.coroutines.delay(50)
            }
            assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            assertFalse(downloads.database.getSongByIdBlocking(id)!!.song.isDownloaded)
        } finally {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
            downloads.database.getSongByIdBlocking(id)?.let { downloads.database.delete(it.song) }
            source.delete()
        }
    }

    @Test fun collectionsCreateSeparateNestedFoldersAndPreserveAudio() = runBlocking {
        val source = File(context.cacheDir, "collection-${UUID.randomUUID()}.m4a")
        val bytes = android.util.Base64.decode(instrumentation.context.assets.open("audio/aac.m4a.base64").use { it.readBytes() }, android.util.Base64.DEFAULT)
        source.writeBytes(bytes)
        val exported = mutableListOf<android.net.Uri>()
        try {
            val title = "Mix / été : \"2026\""
            val folder = collectionFolder(title, UUID.randomUUID().toString())
            assertFalse(folder.contains('/'))
            assertFalse(folder.contains(':'))
            for (kind in listOf("Albums", "Playlists")) {
                val uri = saveAudioToTree(context, tree, source, "001 - Piste.m4a", listOf(kind, folder))
                exported += uri
                assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
                val root = DocumentFile.fromTreeUri(context, tree)!!
                val collection = root.findFile(kind)!!.findFile(folder)!!
                assertTrue(collection.isDirectory)
                assertNotNull(collection.findFile("001 - Piste.m4a"))
            }
            assertNotEquals(exported[0], exported[1])
            assertTrue(runCatching { saveAudioToTree(context, tree, source, "bad.m4a", listOf("..")) }.isFailure)
        } finally {
            exported.forEach { DocumentsContract.deleteDocument(context.contentResolver, it) }
            source.delete()
        }
    }

    @Test(timeout = 90000) fun ytDlpDownloadsRealBytesAndExportsThroughSafWithoutOverwriting() = runBlocking {
        println("Starting native yt-dlp fixture download")
        val bytes = ByteArray(4096) { (it % 127).toByte() }
        // Valid mono PCM WAV, not a renamed text file.
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + bytes.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(bytes.size).array()
        val audio = header + bytes
        val server = ServerSocket(0)
        val responder = thread(isDaemon = true) {
            while (!server.isClosed) try {
                server.accept().use { socket ->
                    socket.soTimeout = 15000
                    val reader = socket.getInputStream().bufferedReader()
                    val first = reader.readLine().orEmpty()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: ${audio.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        if (!first.startsWith("HEAD")) write(audio)
                        flush()
                    }
                }
            } catch (_: Exception) { if (server.isClosed) break }
        }
        val directory = File(context.cacheDir, "audio-test-${UUID.randomUUID()}")
        val exported = mutableListOf<android.net.Uri>()
        try {
            val source = YtDlpAudioEngine.download(context, "http://127.0.0.1:${server.localPort}/sample.wav", directory, "test-${UUID.randomUUID()}") { }
            println("Native download complete; verifying engine version and SAF export")
            val version = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(
                com.yausername.youtubedl_android.YoutubeDLRequest(emptyList<String>()).addOption("--version")
            ).out.trim()
            assertEquals(YtDlpAudioEngine.VERSION, version)
            assertArrayEquals(audio, source.readBytes())
            val name = "audio-${UUID.randomUUID()}.wav"
            repeat(2) { exported += saveAudioToTree(context, tree, source, name) }
            assertNotEquals(exported[0], exported[1])
            println("SAF export and duplicate filename handling complete")
            exported.forEach { uri ->
                assertArrayEquals(audio, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            }
        } finally {
            exported.forEach { DocumentsContract.deleteDocument(context.contentResolver, it) }
            directory.deleteRecursively()
            server.close()
            responder.join(2000)
        }
    }

    @Test(timeout = 90000) fun aacAndOpusKeepTheirRealCodecAndExactBytesAfterExport() = runBlocking {
        for ((asset, mime) in listOf("aac.m4a" to "audio/mp4a-latm", "opus.webm" to "audio/opus")) {
            val audio = android.util.Base64.decode(
                instrumentation.context.assets.open("audio/$asset.base64").use { it.readBytes() },
                android.util.Base64.DEFAULT,
            )
            verifyResolvedAudioRanges(audio, asset)
            val server = ServerSocket(0)
            val responder = thread(isDaemon = true) {
                while (!server.isClosed) try {
                    server.accept().use { socket ->
                        socket.soTimeout = 15000
                        val reader = socket.getInputStream().bufferedReader()
                        val first = reader.readLine().orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) Unit
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: ${audioMime(asset.substringAfterLast('.'))}\r\nContent-Length: ${audio.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            if (!first.startsWith("HEAD")) write(audio)
                            flush()
                        }
                    }
                } catch (_: Exception) { if (server.isClosed) break }
            }
            val directory = File(context.cacheDir, "codec-test-${UUID.randomUUID()}")
            var exported: android.net.Uri? = null
            try {
                val source = YtDlpAudioEngine.download(context,
                    "http://127.0.0.1:${server.localPort}/$asset", directory, "codec-${UUID.randomUUID()}", format = if (asset == "aac.m4a") DownloadAudioFormat.M4A else DownloadAudioFormat.BEST) { }
                if (asset == "aac.m4a") {
                    assertArrayEquals(audio, source.readBytes())
                } else {
                    assertEquals("opus", source.extension)
                    assertEquals("OggS", source.inputStream().use { input -> ByteArray(4).also { input.read(it) } }.toString(Charsets.US_ASCII))
                    val original = File(directory, "original.webm").apply { writeBytes(audio) }
                    assertArrayEquals(audioPackets(original), audioPackets(source))
                    val reused = YtDlpAudioEngine.reuse(context, original, DownloadAudioFormat.BEST,
                        File(directory, "offline"), "remux-${UUID.randomUUID()}")!!
                    assertEquals("opus", reused.extension)
                    assertArrayEquals(audioPackets(original), audioPackets(reused))
                }
                val uri = saveAudioToTree(context, tree, source, "${UUID.randomUUID()}.${source.extension}")
                exported = uri
                assertArrayEquals(source.readBytes(), context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
                val extractor = android.media.MediaExtractor()
                try {
                    extractor.setDataSource(context, uri, null)
                    val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                    assertFalse(formats.any { it.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true })
                    val format = formats.single { it.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                    assertEquals(mime, format.getString(android.media.MediaFormat.KEY_MIME))
                    assertEquals(48000, format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE))
                    assertEquals(2, format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT))
                    println("AUDIO_CODEC_OK: $asset; $format; audio-only output, encoded audio packets preserved")
                } finally { extractor.release() }
            } finally {
                exported?.let { DocumentsContract.deleteDocument(context.contentResolver, it) }
                directory.deleteRecursively()
                server.close()
                responder.join(2000)
            }
        }
    }

    @Test(timeout = 90000) fun flacIsConvertedAndReusableWithoutNetwork() = runBlocking {
        val directory = File(context.cacheDir, "flac-test-${UUID.randomUUID()}").apply { mkdirs() }
        val bytes = android.util.Base64.decode(instrumentation.context.assets.open("audio/aac.m4a.base64").use { it.readBytes() }, android.util.Base64.DEFAULT)
        val source = File(directory, "existing.m4a").apply { writeBytes(bytes) }
        try {
            val output = YtDlpAudioEngine.reuse(context, source, DownloadAudioFormat.FLAC, directory, "flac-${UUID.randomUUID()}")!!
            assertEquals("flac", output.extension)
            assertEquals("fLaC", output.inputStream().use { input -> ByteArray(4).also { input.read(it) } }.toString(Charsets.US_ASCII))
            val extractor = android.media.MediaExtractor()
            try {
                extractor.setDataSource(output.absolutePath)
                val format = extractor.getTrackFormat(0)
                val trackMime = format.getString(android.media.MediaFormat.KEY_MIME)!!
                assertTrue(trackMime in setOf("audio/flac", "audio/raw"))
                assertEquals("audio/flac", output.inputStream().use { sourceAudioMime(it, trackMime) })
                assertEquals(48000, format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE))
                assertEquals(2, format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT))
            } finally { extractor.release() }
            assertEquals(output, YtDlpAudioEngine.reuse(context, output, DownloadAudioFormat.FLAC, directory, "same-flac"))
            assertEquals(source, YtDlpAudioEngine.reuse(context, source, DownloadAudioFormat.M4A, directory, "same-m4a"))
            assertArrayEquals(bytes, source.readBytes())
            val downloads = FolderAudioDownloads.downloadUtil(context)
            val songId = UUID.randomUUID().toString().replace("-", "").take(11)
            val uri = saveAudioToTree(context, tree, output, "$songId.flac")
            try {
                downloads.registerExternalAudio(fr.makoff.music.models.MediaMetadata(songId, "FLAC round trip", emptyList(), 1), uri, output.length())
                val staged = downloads.stageExistingAudio(songId, File(directory, "roundtrip"))!!
                assertEquals("flac", staged.extension)
                assertArrayEquals(output.readBytes(), staged.readBytes())
            } finally {
                (downloads.downloadManager.downloadIndex as androidx.media3.exoplayer.offline.WritableDownloadIndex).removeDownload(songId)
                downloads.downloads.value = downloads.downloads.value - songId
                downloads.database.getSongByIdBlocking(songId)?.let { downloads.database.delete(it.song) }
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            }
            println("FLAC_REAL_OK: native conversion, fLaC header, 48000 Hz stereo, no upsampling")
        } finally { directory.deleteRecursively() }
    }

    @Test(timeout = 30000) fun providerFailureDoesNotLeaveAnIncompleteFile() = runBlocking {
        val source = File(context.cacheDir, "failed-export.wav").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val before = DocumentFile.fromTreeUri(context, tree)!!.listFiles().map { it.uri }.toSet()
        try {
            var failed = false
            try { saveAudioToTree(context, tree, source, "reject-${UUID.randomUUID()}.wav") } catch (_: Exception) { failed = true }
            assertTrue(failed)
            val after = DocumentFile.fromTreeUri(context, tree)!!.listFiles().map { it.uri }.toSet()
            assertEquals(before, after)
        } finally { source.delete() }
    }

    private fun audioPackets(file: File): ByteArray {
        val extractor = android.media.MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).single {
                extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(track)
            val hash = java.security.MessageDigest.getInstance("SHA-256")
            val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                buffer.position(0)
                buffer.limit(size)
                hash.update(buffer)
                extractor.advance()
            }
            return hash.digest()
        } finally { extractor.release() }
    }

    private suspend fun verifyResolvedAudioRanges(audio: ByteArray, asset: String) {
        for (mode in 0..4) {
            val server = ServerSocket(0)
            val responder = thread(isDaemon = true) {
                while (!server.isClosed) try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        reader.readLine()
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val line = reader.readLine().orEmpty()
                            if (line.isEmpty()) break
                            headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                        }
                        val authorized = headers["x-audio-test"] == "kept"
                        val range = Regex("bytes=(\\d+)-(\\d+)").matchEntire(headers["range"].orEmpty())
                        val start = range?.groupValues?.get(1)?.toInt() ?: 0
                        val end = minOf(range?.groupValues?.get(2)?.toInt() ?: audio.lastIndex, audio.lastIndex)
                        val bytes = if (mode == 0) audio else audio.copyOfRange(start, end + 1)
                        val status = if (!authorized || mode == 2) "403 Forbidden" else if (mode == 0) "200 OK" else "206 Partial Content"
                        val declared = bytes.size + if (mode == 3) 1 else 0
                        val contentRange = if (mode == 0 || mode == 2) "" else
                            "Content-Range: bytes ${if (mode == 4) start + 1 else start}-$end/${audio.size}\r\n"
                        socket.getOutputStream().apply {
                            write(("HTTP/1.1 $status\r\n" + contentRange +
                                "Content-Length: $declared\r\nConnection: close\r\n\r\n").toByteArray())
                            write(bytes)
                            flush()
                        }
                    }
                } catch (_: Exception) { if (server.isClosed) break }
            }
            val dir = File(context.cacheDir, "resolved-test-${UUID.randomUUID()}").apply { mkdirs() }
            val destination = File(dir, asset)
            try {
                var failed = false
                try {
                    downloadResolvedAudio(okhttp3.OkHttpClient(), "http://127.0.0.1:${server.localPort}/$asset",
                        mapOf("X-Audio-Test" to "kept"), audio.size.toLong(), destination, 1024)
                } catch (_: Exception) { failed = true }
                if (mode < 2) {
                    assertFalse("Full response and bounded ranges must succeed", failed)
                    assertArrayEquals(audio, destination.readBytes())
                    val exported = saveAudioToTree(context, tree, destination, "${UUID.randomUUID()}-$asset")
                    try {
                        assertArrayEquals(audio, context.contentResolver.openInputStream(exported)!!.use { it.readBytes() })
                    } finally { DocumentsContract.deleteDocument(context.contentResolver, exported) }
                } else {
                    assertTrue("HTTP errors, truncated bodies and shifted ranges must fail", failed)
                    assertFalse(destination.exists())
                }
                assertFalse(File(dir, asset + ".part").exists())
            } finally { server.close(); responder.join(2000); dir.deleteRecursively() }
        }
        println("RESOLVED_DOWNLOAD_OK: $asset; complete ranges, headers, exact bytes, SAF export and failed-transfer cleanup")
    }
}
