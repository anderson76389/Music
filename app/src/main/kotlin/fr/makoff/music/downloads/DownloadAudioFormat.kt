package fr.makoff.music.downloads

import fr.makoff.music.R
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import fr.makoff.music.constants.UnifiedDownloadFormatKey
import fr.makoff.music.constants.InternalDownloadFormatKey
import fr.makoff.music.constants.ExternalDownloadFormatKey
import fr.makoff.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Output format, independent of streaming quality. FLAC cannot restore a lossy source. */
enum class DownloadAudioFormat(val label: Int, val selector: String) {
    BEST(R.string.download_format_best, "bestaudio"),
    M4A(R.string.download_format_m4a, "bestaudio[ext=m4a]"),
    AAC(R.string.download_format_aac, "bestaudio[ext=m4a]"),
    FLAC(R.string.download_format_flac, "bestaudio"),
    ;

    companion object {
        // Previously saved BEST/FLAC selections migrate to portable AAC-in-M4A.
        // The old enum constants stay binary-compatible with existing WorkManager
        // jobs; no new download can silently export Opus, WebM or fake lossless.
        fun parse(value: String?): DownloadAudioFormat = when (value) {
            AAC.name -> AAC
            else -> M4A
        }
        val portableChoices: List<DownloadAudioFormat> = listOf(M4A, AAC)
    }
}

/** Exact yt-dlp format expression. BEST always targets the highest audio
 * available to this session; a low-quality request is explicit only.
 * Local fixtures use "best" to exercise the file input path.
 */
internal fun selectYtDlpAudioFormat(format: DownloadAudioFormat, lowQuality: Boolean, isLocalSource: Boolean): String =
    if (isLocalSource) "best" else "bestaudio[ext=m4a]"


/** Android's FLAC MediaExtractor may expose decoded PCM; preserve the source codec. */
internal fun sourceAudioMime(input: java.io.InputStream, trackMime: String): String {
    val header = ByteArray(4)
    java.io.DataInputStream(input).readFully(header)
    return if (header.toString(Charsets.US_ASCII) == "fLaC") "audio/flac" else trackMime
}

/** Old BEST/FLAC/Opus preferences are migrated at read time without data loss. */
internal fun Preferences.selectedDownloadFormat(): DownloadAudioFormat =
    DownloadAudioFormat.parse(
        this[UnifiedDownloadFormatKey]
            ?: this[ExternalDownloadFormatKey]
            ?: this[InternalDownloadFormatKey],
    )

internal fun Context.selectedDownloadFormat(): DownloadAudioFormat = runBlocking(Dispatchers.IO) {
    dataStore.data.first().selectedDownloadFormat()
}
