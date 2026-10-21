package fr.makoff.music.downloads

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
                    "http://127.0.0.1:${server.localPort}/$asset", directory, "codec-${UUID.randomUUID()}") { }
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
