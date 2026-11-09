package fr.makoff.music.downloads

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.net.SocketException
import java.util.UUID
import kotlin.concurrent.thread

/** Local deterministic HTTP ranged audio transfer: no YouTube account/network required. */
@RunWith(AndroidJUnit4::class)
class DownloadRangeInstrumentedTest {
    private val context get() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext

    private fun serve(server: ServerSocket, data: ByteArray, invalidOffset: Boolean) = thread(name = "audio-range-fixture") {
        try {
            server.use { listener ->
                while (!Thread.currentThread().isInterrupted) {
                    listener.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        val request = reader.readLine().orEmpty()
                        check(request.startsWith("GET "))
                        var range = ""
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) break
                            if (line.startsWith("Range:", ignoreCase = true))
                                range = line.substringAfter(':').trim()
                        }
                        val values = Regex("bytes=(\\d+)-(\\d+)").matchEntire(range)
                            ?: error("Expected bounded Range request, got: $range")
                        val requestedStart = values.groupValues[1].toInt()
                        val requestedEnd = minOf(values.groupValues[2].toInt(), data.lastIndex)
                        val actualStart = requestedStart + if (invalidOffset) 1 else 0
                        val chunk = if (actualStart <= requestedEnd) data.copyOfRange(actualStart, requestedEnd + 1) else byteArrayOf()
                        val headers = ("HTTP/1.1 206 Partial Content\r\n" +
                            "Content-Type: audio/mp4\r\n" +
                            "Content-Range: bytes $actualStart-$requestedEnd/${data.size}\r\n" +
                            "Content-Length: ${chunk.size}\r\n" +
                            "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
                        socket.getOutputStream().use { out ->
                            out.write(headers)
                            out.write(chunk)
                            out.flush()
                        }
                    }
                }
            }
        } catch (_: SocketException) {
            // The test closes the listener once the client has finished.
        }
    }

    @Test(timeout = 25000) fun everyRangeIsCopiedByteForByteWithoutReencoding() = runBlocking {
        val data = ByteArray(51_431) { ((it * 31 + 17) and 0xff).toByte() }
        val destination = File(context.cacheDir, "audio-ranged-${UUID.randomUUID()}.m4a")
        val server = ServerSocket(0).apply { soTimeout = 10_000 }
        val worker = serve(server, data, invalidOffset = false)
        var finalPercent = -1f
        try {
            val result = downloadResolvedAudio(
                OkHttpClient(), "http://127.0.0.1:${server.localPort}/audio", emptyMap(),
                data.size.toLong(), destination, chunkSize = 4096L,
            ) { finalPercent = it }
            assertArrayEquals("Audio bytes must be lossless and complete", data, result.readBytes())
            assertTrue(finalPercent == 100f)
            assertFalse(File(destination.absolutePath + ".part").exists())
            println("AUDIO_RANGES_VERIFIED: bytes=${result.length()}; last_progress=$finalPercent")
        } finally {
            server.close()
            worker.join(2000)
            destination.delete()
            File(destination.absolutePath + ".part").delete()
        }
    }

    @Test(timeout = 15000) fun incorrectRangeOffsetRejectsAndRemovesPartialFile() = runBlocking {
        val data = ByteArray(8192) { (it and 0xff).toByte() }
        val destination = File(context.cacheDir, "audio-invalid-${UUID.randomUUID()}.m4a")
        val server = ServerSocket(0).apply { soTimeout = 5000 }
        val worker = serve(server, data, invalidOffset = true)
        try {
            val result = runCatching {
                downloadResolvedAudio(OkHttpClient(),
                    "http://127.0.0.1:${server.localPort}/invalid", emptyMap(),
                    data.size.toLong(), destination, chunkSize = 1024L)
            }
            assertTrue("Bad Content-Range must never produce a completed download", result.isFailure)
            assertFalse(destination.exists())
            assertFalse(File(destination.absolutePath + ".part").exists())
        } finally {
            server.close()
            worker.join(2000)
            destination.delete()
            File(destination.absolutePath + ".part").delete()
        }
    }
}
