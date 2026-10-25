package fr.makoff.music.downloads

import androidx.datastore.preferences.core.edit
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

    @Test(timeout = 90000) fun likingAutomaticallyExportsToLikedFolderAndKeepsOfflineReference() = runBlocking {
        val downloads = FolderAudioDownloads.downloadUtil(context)
        val id = UUID.randomUUID().toString().replace("-", "").take(11)
        val media = fr.makoff.music.models.MediaMetadata(id, "Liked audio test", emptyList(), 1)
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
            assertArrayEquals(bytes, context.contentResolver.openInputStream(exported!!)!!.use { it.readBytes() })
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
                assertArrayEquals(audio, source.readBytes())
                val uri = saveAudioToTree(context, tree, source, "${UUID.randomUUID()}-$asset")
                exported = uri
                assertArrayEquals(audio, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
                val extractor = android.media.MediaExtractor()
                try {
                    extractor.setDataSource(context, uri, null)
                    val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
                    val format = formats.single { it.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                    assertEquals(mime, format.getString(android.media.MediaFormat.KEY_MIME))
                    assertEquals(48000, format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE))
                    assertEquals(2, format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT))
                    println("AUDIO_CODEC_OK: $asset; $format; source and exported bytes identical")
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
}
