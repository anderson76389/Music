package fr.makoff.music.downloads

import fr.makoff.music.R
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import fr.makoff.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** One portable download format: the highest available original AAC in M4A.
 * AAC is the codec, M4A its broadly supported container (PCs, MP3 players).
 * The previous BEST/FLAC preferences are migrated on read; no Opus or
 * misleading lossless transcode is presented to the user.
 */
enum class DownloadAudioFormat(val label: Int, val selector: String) {
    M4A(R.string.download_format_m4a, "bestaudio[ext=m4a]");

    companion object {
        fun parse(value: String?): DownloadAudioFormat = M4A
    }
}

/** Never fall back to Opus or a worse AAC track. Unsupported tracks fail
 * clearly rather than silently producing an incompatible file.
 */
internal fun selectYtDlpAudioFormat(
    format: DownloadAudioFormat,
    lowQuality: Boolean,
    isLocalSource: Boolean,
): String = if (isLocalSource) "best" else format.selector

/** Android's FLAC MediaExtractor may expose decoded PCM; preserve the source codec. */
internal fun sourceAudioMime(input: java.io.InputStream, trackMime: String): String {
    val header = ByteArray(4)
    java.io.DataInputStream(input).readFully(header)
    return if (header.toString(Charsets.US_ASCII) == "fLaC") "audio/flac" else trackMime
}

/** Migrate legacy BEST/FLAC preference values safely to portable AAC/M4A.
 * Existing cached files need not be deleted; only new downloads are affected.
 */
internal fun Preferences.selectedDownloadFormat(): DownloadAudioFormat = DownloadAudioFormat.M4A

internal fun Context.selectedDownloadFormat(): DownloadAudioFormat = runBlocking(Dispatchers.IO) {
    dataStore.data.first().selectedDownloadFormat()
}
