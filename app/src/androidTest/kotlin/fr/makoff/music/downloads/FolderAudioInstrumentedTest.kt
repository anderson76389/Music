package fr.makoff.music.downloads

import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
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

    @Test fun ytDlpDownloadsRealBytesAndExportsThroughSafWithoutOverwriting() = runBlocking {
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
            assertArrayEquals(audio, source.readBytes())
            val name = "audio-${UUID.randomUUID()}.wav"
            repeat(2) { exported += saveAudioToTree(context, tree, source, name) }
            assertNotEquals(exported[0], exported[1])
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

    @Test fun providerFailureDoesNotLeaveAnIncompleteFile() = runBlocking {
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
