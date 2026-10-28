package fr.makoff.music.downloads

import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.makoff.music.models.MediaMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Compatibility probe against the unmodified official Auxio APK, using synthetic audio only. */
@RunWith(AndroidJUnit4::class)
class AuxioExportCompatibilityTest {
    @Test(timeout = 240000) fun officialAuxioDecodesTaggedAlbumWithoutSkipping() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "auxio-export-${UUID.randomUUID()}").apply { mkdirs() }
        val documents = mutableListOf<Pair<Uri, String>>()
        try {
            val cover = File(directory, "cover.jpg")
            val bitmap = android.graphics.Bitmap.createBitmap(1024, 1024, android.graphics.Bitmap.Config.ARGB_8888)
            val random = java.util.Random(42)
            bitmap.setPixels(IntArray(1024 * 1024) {
                android.graphics.Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            }, 0, 1024, 0, 0, 1024, 1024)
            cover.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
            bitmap.recycle()
            val album = "Validation Auxio ${UUID.randomUUID()}"
            val formats = listOf(DownloadAudioFormat.BEST, DownloadAudioFormat.BEST, DownloadAudioFormat.M4A, DownloadAudioFormat.FLAC)
            for ((index, format) in formats.withIndex()) {
                val work = File(directory, index.toString()).apply { mkdirs() }
                val asset = if (format == DownloadAudioFormat.BEST) "player-opus.webm" else "player-aac.m4a"
                val raw = File(work, asset).apply { writeBytes(android.util.Base64.decode(
                    instrumentation.context.assets.open("audio/$asset.base64").use { it.readBytes() }, android.util.Base64.DEFAULT)) }
                val source = requireNotNull(YtDlpAudioEngine.reuse(context, raw, format, work, "auxio-$index"))
                val title = "Export Auxio $index ${format.name}"
                val media = MediaMetadata("auxioTest0$index".padEnd(11, '0'), title,
                    listOf(MediaMetadata.Artist(null, "Validation locale")), 20,
                    thumbnailUrl = Uri.fromFile(cover).toString(), album = MediaMetadata.Album("test-album", album, 2023))
                val tagged = tagAudioFile(context, source, media, index + 1, work)
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.${tagged.extension}")
                    put(MediaStore.Audio.Media.MIME_TYPE, audioMime(tagged.extension))
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Validation-exports")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = requireNotNull(context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values))
                documents += uri to title
                requireNotNull(context.contentResolver.openOutputStream(uri, "w")).use { output -> tagged.inputStream().use { it.copyTo(output) } }
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                assertArrayEquals(tagged.readBytes(), requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() })
            }
            instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL")
            val sessions = context.getSystemService(MediaSessionManager::class.java)
            fun controller(): MediaController? = sessions.getActiveSessions(null).firstOrNull { it.packageName == "org.oxycblt.auxio" }
            for ((uri, title) in documents) {
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri))
                    .setComponent(ComponentName("org.oxycblt.auxio", "org.oxycblt.auxio.MainActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
                val deadline = SystemClock.elapsedRealtime() + 45000
                var playing = false
                while (!playing && SystemClock.elapsedRealtime() < deadline) {
                    val player = controller()
                    val actualTitle = player?.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                    val state = player?.playbackState
                    playing = actualTitle == title && state?.state == PlaybackState.STATE_PLAYING && state.position > 700
                    SystemClock.sleep(100)
                }
                assertTrue("Official Auxio cannot render $title; current state=${controller()?.playbackState}", playing)
                SystemClock.sleep(1200)
                assertEquals("Track must not be skipped after starting", title,
                    controller()?.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE))
                assertTrue(requireNotNull(controller()?.playbackState).position > 1500)
                controller()?.transportControls?.pause()
                println("AUXIO_EXPORT_PLAYBACK_OK: $title; real public file, full cover, date, stable playback")
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            documents.forEach { (uri, _) -> runCatching { context.contentResolver.delete(uri, null, null) } }
            directory.deleteRecursively()
        }
    }
}
